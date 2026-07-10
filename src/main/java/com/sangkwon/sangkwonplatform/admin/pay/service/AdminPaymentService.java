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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPaymentService {

    // 회원 검색어로 좁힐 때 IN 절에 넣을 회원 수 상한. 넘치면 검색어를 더 좁히면 된다.
    private static final int MEMBER_MATCH_LIMIT = 100;

    private final PaymentOrderRepository paymentOrderRepository;
    private final MemberRepository memberRepository;

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
        return new PaymentSummaryResponse(
                paymentOrderRepository.sumAmountByStatusSince(PaymentStatus.PAID, monthStart),
                paymentOrderRepository.countByStatusAndApprovedAtGreaterThanEqual(PaymentStatus.PAID, monthStart),
                memberRepository.countByPlanUntilAfter(LocalDateTime.now()),
                pending + paid + failed,
                pending,
                paid,
                failed);
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
