package com.sangkwon.sangkwonplatform.admin.pay.dto.response;

import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;

import java.time.LocalDateTime;

// 관리자 결제 목록의 한 행. 회원이 하드삭제된 주문은 member가 null로 들어온다(loginId/nickname null).
public record AdminPaymentResponse(
        String orderId,
        Long memberId,
        String loginId,
        String nickname,
        String planCd,
        BillingCycle billingCycle,
        long amount,
        PaymentStatus status,
        String orderName,
        LocalDateTime approvedAt,
        LocalDateTime createdAt
) {
    public static AdminPaymentResponse from(PaymentOrder o, Member member) {
        return new AdminPaymentResponse(
                o.getOrderId(),
                o.getMemberId(),
                member == null ? null : member.getLoginId(),
                member == null ? null : member.getNickname(),
                o.getPlanCd(),
                o.getBillingCycle(),
                o.getAmount(),
                o.getStatus(),
                o.getOrderName(),
                o.getApprovedAt(),
                o.getCreatedAt());
    }
}
