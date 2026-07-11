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
    private static final ObjectMapper ERROR_BODY_MAPPER = new ObjectMapper();

    private final PaymentOrderRepository paymentOrderRepository;
    private final MemberRepository memberRepository;
    private final RestClient restClient;
    private final String tossSecretKey;

    public AdminPaymentService(PaymentOrderRepository paymentOrderRepository,
                               MemberRepository memberRepository,
                               RestClient restClient,
                               @Value("${toss.secret-key}") String tossSecretKey) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.memberRepository = memberRepository;
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

        order.canceled();
        paymentOrderRepository.save(order);
        revokeSubscription(order.getMemberId());
        return toResponse(order);
    }

    // 환불이면 이 주문으로 부여했던 구독을 회수한다. 하드삭제된 회원의 주문은 대상이 없다.
    private void revokeSubscription(Long memberId) {
        if (memberId == null) {
            return;
        }
        memberRepository.findById(memberId).ifPresent(member -> {
            member.revokePro();
            memberRepository.save(member);
        });
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
