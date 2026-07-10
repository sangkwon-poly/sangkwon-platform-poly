package com.sangkwon.sangkwonplatform.member.dto.response;

import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;

// 결제창 렌더에 필요한 주문 정보. clientKey는 공개 키라 프론트에 내려도 된다.
public record PaymentOrderResponse(
        String orderId,
        String orderName,
        long amount,
        String clientKey
) {
    public static PaymentOrderResponse from(PaymentOrder order, String clientKey) {
        return new PaymentOrderResponse(order.getOrderId(), order.getOrderName(), order.getAmount(), clientKey);
    }
}
