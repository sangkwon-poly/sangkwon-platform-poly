package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.dto.request.PaymentConfirmRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.PaymentOrderCreateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.PaymentConfigResponse;
import com.sangkwon.sangkwonplatform.member.dto.response.PaymentConfirmResponse;
import com.sangkwon.sangkwonplatform.member.dto.response.PaymentOrderResponse;
import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

// 요금제 결제(토스페이먼츠). 금액은 서버가 확정하고, 승인 전에 주문 금액과 대조해 위변조를 막는다.
@Service
public class PaymentService {

    // 요금은 화면이 아니라 서버가 결정한다. 요금제 화면(pricing)과 값이 함께 움직여야 한다.
    // 연간은 월간 x 10개월(2개월 무료), 월 환산 20,000원.
    private static final long PRO_MONTHLY_AMOUNT = 24_000L;
    private static final long PRO_YEARLY_AMOUNT = 240_000L;

    // 토스가 '이미 승인된 결제'라고 알리는 오류 코드. 실패가 아니라 승인된 주문으로 처리해야 한다.
    private static final String TOSS_ALREADY_PROCESSED = "ALREADY_PROCESSED_PAYMENT";
    private static final ObjectMapper ERROR_BODY_MAPPER = new ObjectMapper();

    private final PaymentOrderRepository paymentOrderRepository;
    private final MemberRepository memberRepository;
    private final RestClient restClient;
    private final String clientKey;
    private final String secretKey;

    public PaymentService(PaymentOrderRepository paymentOrderRepository,
                          MemberRepository memberRepository,
                          RestClient restClient,
                          @Value("${toss.client-key}") String clientKey,
                          @Value("${toss.secret-key}") String secretKey) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.memberRepository = memberRepository;
        this.restClient = restClient;
        this.clientKey = clientKey;
        this.secretKey = secretKey;
    }

    // 위젯 렌더용 공개 설정. 주문은 결제 버튼을 누를 때 만들어 빈 주문이 쌓이지 않게 한다.
    public PaymentConfigResponse config() {
        requireConfigured();
        return new PaymentConfigResponse(clientKey);
    }

    // 주문 생성: PENDING으로 저장해 두고, 승인 단계에서 이 금액과 대조한다.
    public PaymentOrderResponse createOrder(Long memberId, PaymentOrderCreateRequest req) {
        requireAuth(memberId);
        requireConfigured();
        if (!"PRO".equals(req.plan())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        BillingCycle cycle = parseCycle(req.billingCycle());
        // 이미 Pro인 회원은 신규 결제를 막는다. 연장은 어드민 부여로만 처리한다.
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.isPro()) {
            throw new BusinessException(ErrorCode.ALREADY_PRO);
        }
        long amount = cycle == BillingCycle.YEARLY ? PRO_YEARLY_AMOUNT : PRO_MONTHLY_AMOUNT;
        String orderName = "여기콕 Pro " + (cycle == BillingCycle.YEARLY ? "연간" : "월간");
        String orderId = UUID.randomUUID().toString().replace("-", "");

        PaymentOrder order = PaymentOrder.create(orderId, memberId, "PRO", cycle, amount, orderName);
        paymentOrderRepository.save(order);
        return PaymentOrderResponse.from(order, clientKey);
    }

    // 승인: 외부 HTTP 동안 DB 커넥션을 잡지 않도록 트랜잭션으로 묶지 않는다(상태 저장은 save 한 번).
    public PaymentConfirmResponse confirm(Long memberId, PaymentConfirmRequest req) {
        requireAuth(memberId);
        requireConfigured();
        PaymentOrder order = paymentOrderRepository.findByOrderIdAndMemberId(req.orderId(), memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));

        // 성공 페이지 새로고침 등으로 승인이 중복 호출되어도 멱등하게 응답한다
        if (order.getStatus() == PaymentStatus.PAID) {
            return PaymentConfirmResponse.from(order);
        }
        // 리다이렉트 쿼리의 금액이 주문과 다르면 조작으로 보고 거절한다
        if (req.amount() != order.getAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        JsonNode res;
        try {
            res = restClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/confirm")
                    .header("Authorization", basicAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("paymentKey", req.paymentKey(), "orderId", req.orderId(), "amount", req.amount()))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            // 토스가 요청을 받아 4xx로 거절했다. 단 ALREADY_PROCESSED는 실패가 아니라 이미 승인된 주문이다.
            if (isAlreadyProcessed(e)) {
                return reconcileApproved(order.getOrderId(), req.paymentKey());
            }
            markFailedIfPending(order.getOrderId());
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        } catch (RestClientException e) {
            // 5xx / 타임아웃 / 네트워크 오류: 승인 여부가 불확실하다. 여기서 FAILED로 단정하면
            // '카드는 결제됐는데 실패로 기록'되는 불일치가 생기므로 PENDING을 유지하고 대사(재확인)에 맡긴다.
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_PENDING);
        }
        if (res == null) {
            // 2xx인데 본문이 비었다: 상태 불명이므로 실패로 단정하지 않는다.
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_PENDING);
        }

        order.paid(req.paymentKey(), parseApprovedAt(res.path("approvedAt").asText(null)));
        try {
            paymentOrderRepository.save(order);
        } catch (OptimisticLockingFailureException e) {
            // 동시 승인에서 다른 스레드가 먼저 확정했다. 최신 상태로 멱등 응답한다.
            return reloadConfirmed(order.getOrderId());
        }
        activateSubscription(memberId, order.getBillingCycle());
        return PaymentConfirmResponse.from(order);
    }

    // 토스가 이미 승인했다고 알린 주문을 우리 쪽 상태와 맞춘다. 아직 PAID가 아니면 확정하고 구독을 켠다.
    private PaymentConfirmResponse reconcileApproved(String orderId, String paymentKey) {
        PaymentOrder order = paymentOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
        if (order.getStatus() == PaymentStatus.PAID) {
            return PaymentConfirmResponse.from(order);
        }
        order.paid(paymentKey, LocalDateTime.now());
        try {
            paymentOrderRepository.save(order);
        } catch (OptimisticLockingFailureException e) {
            return reloadConfirmed(orderId);
        }
        activateSubscription(order.getMemberId(), order.getBillingCycle());
        return PaymentConfirmResponse.from(order);
    }

    // PENDING일 때만 FAILED로 내린다. 동시 승인의 승자가 이미 PAID로 만든 주문은 건드리지 않는다.
    private void markFailedIfPending(String orderId) {
        paymentOrderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() != PaymentStatus.PENDING) {
                return;
            }
            order.failed();
            try {
                paymentOrderRepository.save(order);
            } catch (OptimisticLockingFailureException ignore) {
                // 다른 스레드가 먼저 확정했다. FAILED로 덮지 않는다.
            }
        });
    }

    private PaymentConfirmResponse reloadConfirmed(String orderId) {
        PaymentOrder order = paymentOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
        return PaymentConfirmResponse.from(order);
    }

    private boolean isAlreadyProcessed(HttpClientErrorException e) {
        try {
            JsonNode body = ERROR_BODY_MAPPER.readTree(e.getResponseBodyAsString(StandardCharsets.UTF_8));
            return TOSS_ALREADY_PROCESSED.equals(body.path("code").asText(null));
        } catch (RuntimeException ignore) {
            return false;
        }
    }

    // 결제 확정과 동시에 구독을 켠다. 만료 전 재구독이면 남은 기간에 이어붙여 손해가 없게 한다.
    // confirm은 외부 HTTP 때문에 트랜잭션으로 묶지 않으므로, 회원 변경은 save로 명시 반영한다.
    private void activateSubscription(Long memberId, BillingCycle cycle) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        LocalDateTime base = member.isPro() ? member.getPlanUntil() : LocalDateTime.now();
        LocalDateTime until = cycle == BillingCycle.YEARLY ? base.plusYears(1) : base.plusMonths(1);
        member.activatePro(until);
        memberRepository.save(member);
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    private LocalDateTime parseApprovedAt(String iso) {
        if (iso == null || iso.isBlank()) {
            return LocalDateTime.now();
        }
        return OffsetDateTime.parse(iso).toLocalDateTime();
    }

    private BillingCycle parseCycle(String raw) {
        try {
            return BillingCycle.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void requireAuth(Long memberId) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }

    // 키가 없으면 데모 환경 미설정: 500 대신 안내 메시지로 답한다
    private void requireConfigured() {
        if (clientKey == null || clientKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
    }
}
