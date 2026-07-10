package com.sangkwon.sangkwonplatform.member.dto.response;

import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentConfirmResponse(
        String orderId,
        String orderName,
        String plan,
        BillingCycle billingCycle,
        long amount,
        PaymentStatus status,
        LocalDateTime approvedAt
) {
    public static PaymentConfirmResponse from(PaymentOrder order) {
        return new PaymentConfirmResponse(
                order.getOrderId(),
                order.getOrderName(),
                order.getPlanCd(),
                order.getBillingCycle(),
                order.getAmount(),
                order.getStatus(),
                order.getApprovedAt()
        );
    }
}
