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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

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
        } catch (Exception e) {
            markFailed(order);
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
        if (res == null) {
            markFailed(order);
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }

        order.paid(req.paymentKey(), parseApprovedAt(res.path("approvedAt").asText(null)));
        paymentOrderRepository.save(order);
        activateSubscription(memberId, order.getBillingCycle());
        return PaymentConfirmResponse.from(order);
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

    private void markFailed(PaymentOrder order) {
        order.failed();
        paymentOrderRepository.save(order);
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
