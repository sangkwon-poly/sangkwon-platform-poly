package com.sangkwon.sangkwonplatform.admin.pay.service;

import com.sangkwon.sangkwonplatform.admin.pay.dto.response.AdminPaymentResponse;
import com.sangkwon.sangkwonplatform.admin.pay.dto.response.PaymentPageResponse;
import com.sangkwon.sangkwonplatform.admin.pay.dto.response.PaymentSummaryResponse;
import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import com.sangkwon.sangkwonplatform.member.service.PaymentActivationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminPaymentService {

    // 회원 검색어로 좁힐 때 IN 절에 넣을 회원 수 상한. 넘치면 검색어를 더 좁히면 된다.
    private static final int MEMBER_MATCH_LIMIT = 100;

    // 토스가 '이미 취소된 결제'라고 알리는 오류 코드. 상태 드리프트를 맞추기 위해 성공으로 취급한다.
    private static final String TOSS_ALREADY_CANCELED = "ALREADY_CANCELED_PAYMENT";
    // 토스에 결제 기록 자체가 없을 때의 오류 코드(결제창만 열고 완료 안 한 주문).
    private static final String TOSS_NOT_FOUND = "NOT_FOUND_PAYMENT";
    private static final ObjectMapper ERROR_BODY_MAPPER = new ObjectMapper();

    private final PaymentOrderRepository paymentOrderRepository;
    private final MemberRepository memberRepository;
    private final PaymentActivationService paymentActivationService;
    private final RestClient restClient;
    private final String tossSecretKey;

    public AdminPaymentService(PaymentOrderRepository paymentOrderRepository,
                               MemberRepository memberRepository,
                               PaymentActivationService paymentActivationService,
                               RestClient restClient,
                               @Value("${toss.secret-key}") String tossSecretKey) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.memberRepository = memberRepository;
        this.paymentActivationService = paymentActivationService;
        this.restClient = restClient;
        this.tossSecretKey = tossSecretKey;
    }

    // 결제 목록: 상태·주기 필터 + 회원(아이디/이메일/닉네임) 검색. 회원 표시명은 모아서 한 번에 매핑한다.
    public PaymentPageResponse getOrders(String keyword, PaymentStatus status, BillingCycle cycle, Pageable pageable) {
        String kw = normalize(keyword);
        Page<PaymentOrder> orders;
        if (kw == null) {
            orders = paymentOrderRepository.searchForAdmin(status, cycle, pageable);
        } else {
            List<Long> memberIds = memberRepository.findIdsByKeyword(kw, PageRequest.of(0, MEMBER_MATCH_LIMIT));
            if (memberIds.isEmpty()) {
                return PaymentPageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
            }
            orders = paymentOrderRepository.searchForAdminByMembers(status, cycle, memberIds, pageable);
        }

        Map<Long, Member> memberById = memberRepository.findAllById(
                        orders.getContent().stream().map(PaymentOrder::getMemberId)
                                .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Member::getMemberId, Function.identity()));
        List<AdminPaymentResponse> content = orders.getContent().stream()
                .map(o -> AdminPaymentResponse.from(o, o.getMemberId() == null ? null : memberById.get(o.getMemberId())))
                .toList();
        return new PaymentPageResponse(content, orders.getNumber(), orders.getSize(),
                orders.getTotalElements(), orders.getTotalPages());
    }

    // 상단 지표 + 상태 칩 카운트. 매출과 결제 건수는 이번 달 승인 기준.
    public PaymentSummaryResponse getSummary() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Map<PaymentStatus, Long> byStatus = new EnumMap<>(PaymentStatus.class);
        for (PaymentOrderRepository.PaymentStatusCount row : paymentOrderRepository.countGroupByStatus()) {
            byStatus.put(row.getStatus(), row.getCnt());
        }
        long pending = byStatus.getOrDefault(PaymentStatus.PENDING, 0L);
        long paid = byStatus.getOrDefault(PaymentStatus.PAID, 0L);
        long failed = byStatus.getOrDefault(PaymentStatus.FAILED, 0L);
        long canceled = byStatus.getOrDefault(PaymentStatus.CANCELED, 0L);
        return new PaymentSummaryResponse(
                paymentOrderRepository.sumAmountByStatusSince(PaymentStatus.PAID, monthStart),
                paymentOrderRepository.countByStatusAndApprovedAtGreaterThanEqual(PaymentStatus.PAID, monthStart),
                memberRepository.countByPlanUntilAfter(LocalDateTime.now()),
                pending + paid + failed + canceled,
                pending,
                paid,
                failed,
                canceled);
    }

    // 환불(토스 결제취소). 외부 HTTP 동안 트랜잭션을 잡지 않도록 트랜잭션 밖에서 실행하고 상태는 save로 반영한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminPaymentResponse cancel(String orderId, String reason) {
        PaymentOrder order = paymentOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결제 주문을 찾을 수 없습니다."));
        if (order.getStatus() == PaymentStatus.CANCELED) {
            return toResponse(order); // 이미 환불됨: 멱등
        }
        if (order.getStatus() != PaymentStatus.PAID || order.getPaymentKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "결제 완료(PAID) 상태의 주문만 환불할 수 있습니다.");
        }

        try {
            restClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/{paymentKey}/cancel", order.getPaymentKey())
                    .header("Authorization", basicAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancelReason", reason))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            // 이미 토스에서 취소된 결제면 상태만 맞추고 진행한다. 그 외 4xx는 환불 실패.
            if (!isAlreadyCanceled(e)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제 취소에 실패했습니다.");
            }
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제 취소 요청을 보내지 못했습니다.");
        }

        // 주문 CANCELED 저장과 구독 회수를 한 트랜잭션으로 원자화한다(회수 실패 후 Pro 잔존 방지).
        // 환불은 PAID 주문만 허용하므로(위 가드) 활성화된 적 있음이 보장된다.
        paymentActivationService.finalizeCanceled(order, true);
        return toResponse(order);
    }

    // 유실 주문 대사: 토스의 실제 결제 상태를 조회해 DB와 맞춘다. 웹훅이 없어 생기는 불일치를 관리자가 정정하는 경로.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReconcileResult reconcile(String orderId) {
        PaymentOrder order = paymentOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결제 주문을 찾을 수 없습니다."));
        PaymentStatus before = order.getStatus();

        JsonNode toss = fetchTossPayment(orderId);
        // 토스에 결제 기록이 없으면 결제창만 열고 완료 안 한 주문이다. 진행 중이던 PENDING만 실패로 확정한다.
        PaymentStatus target = (toss == null)
                ? (before == PaymentStatus.PENDING ? PaymentStatus.FAILED : before)
                : mapTossStatus(toss.path("status").asText(null));

        if (target == null || target == before || !isSafeTransition(before, target)) {
            return new ReconcileResult(before, before, toResponse(order));
        }
        applyReconciled(order, before, target, toss);
        return new ReconcileResult(before, order.getStatus(), toResponse(order));
    }

    // 자동 대사가 하면 안 되는 전이를 막는다.
    // - PAID -> FAILED: PAID를 임의로 강등하면 손실이 크다. 토스가 실패/만료라 해도 자동으로 내리지 않는다(수동 확인).
    // - CANCELED -> PAID: 환불 완료를 다시 승인으로 되돌리면 환불이 무효화되고 Pro가 무료 재부여된다(finalizePaid와 이중 방어).
    private static boolean isSafeTransition(PaymentStatus before, PaymentStatus target) {
        if (before == PaymentStatus.PAID && target == PaymentStatus.FAILED) {
            return false;
        }
        if (before == PaymentStatus.CANCELED && target == PaymentStatus.PAID) {
            return false;
        }
        return true;
    }

    private void applyReconciled(PaymentOrder order, PaymentStatus before, PaymentStatus target, JsonNode toss) {
        switch (target) {
            case PAID -> {
                String key = toss == null ? order.getPaymentKey() : toss.path("paymentKey").asText(order.getPaymentKey());
                LocalDateTime approvedAt = toss == null
                        ? LocalDateTime.now() : parseApprovedAt(toss.path("approvedAt").asText(null));
                // 주문 PAID 확정 + 구독 활성화를 한 트랜잭션으로 원자화(회원 승인 경로와 동일 규칙).
                // 전달한 order 객체를 그대로 PAID로 바꾸므로 아래 ReconcileResult가 정정된 상태를 반영한다.
                paymentActivationService.finalizePaid(order, key, approvedAt);
            }
            case CANCELED -> {
                // 주문 CANCELED + 구독 회수를 원자화한다. 회수는 이 주문이 활성화된 적 있을 때(PAID였을 때)만 한다.
                // PENDING/FAILED에서 넘어온 취소는 planUntil에 기여한 적이 없어, 회수하면 정상 유료기간을 깎는다.
                paymentActivationService.finalizeCanceled(order, before == PaymentStatus.PAID);
            }
            case FAILED -> {
                order.failed();
                paymentOrderRepository.save(order);
            }
            default -> { }
        }
    }

    private JsonNode fetchTossPayment(String orderId) {
        try {
            return restClient.get()
                    .uri("https://api.tosspayments.com/v1/payments/orders/{orderId}", orderId)
                    .header("Authorization", basicAuth())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            if (isNotFoundPayment(e)) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "토스 결제 조회에 실패했습니다.");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "토스 결제 조회 요청을 보내지 못했습니다.");
        }
    }

    private static PaymentStatus mapTossStatus(String tossStatus) {
        if (tossStatus == null) {
            return null;
        }
        return switch (tossStatus) {
            case "DONE" -> PaymentStatus.PAID;
            case "CANCELED", "PARTIAL_CANCELED" -> PaymentStatus.CANCELED;
            case "ABORTED", "EXPIRED" -> PaymentStatus.FAILED;
            default -> null; // READY / IN_PROGRESS / WAITING_FOR_DEPOSIT: 아직 진행 중, 변경하지 않는다
        };
    }

    private boolean isNotFoundPayment(HttpClientErrorException e) {
        try {
            JsonNode body = ERROR_BODY_MAPPER.readTree(e.getResponseBodyAsString(StandardCharsets.UTF_8));
            return TOSS_NOT_FOUND.equals(body.path("code").asText(null));
        } catch (RuntimeException ignore) {
            return false;
        }
    }

    private LocalDateTime parseApprovedAt(String iso) {
        if (iso == null || iso.isBlank()) {
            return LocalDateTime.now();
        }
        return OffsetDateTime.parse(iso).toLocalDateTime();
    }

    // 대사 결과: 이전/이후 상태(감사용)와 정정된 주문.
    public record ReconcileResult(PaymentStatus before, PaymentStatus after, AdminPaymentResponse order) {
    }


    private AdminPaymentResponse toResponse(PaymentOrder order) {
        Member member = order.getMemberId() == null ? null
                : memberRepository.findById(order.getMemberId()).orElse(null);
        return AdminPaymentResponse.from(order, member);
    }

    private boolean isAlreadyCanceled(HttpClientErrorException e) {
        try {
            JsonNode body = ERROR_BODY_MAPPER.readTree(e.getResponseBodyAsString(StandardCharsets.UTF_8));
            return TOSS_ALREADY_CANCELED.equals(body.path("code").asText(null));
        } catch (RuntimeException ignore) {
            return false;
        }
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    // 검색어 정규화는 회원 관리와 같은 규칙: 소문자화 + LIKE 와일드카드 이스케이프 + %감싸기.
    private String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
