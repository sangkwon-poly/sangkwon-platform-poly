package com.sangkwon.sangkwonplatform.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 결제 승인 요청. successUrl 리다이렉트 쿼리(paymentKey/orderId/amount)를 그대로 전달받는다.
public record PaymentConfirmRequest(
        @NotBlank(message = "paymentKey가 없습니다.")
        String paymentKey,

        @NotBlank(message = "orderId가 없습니다.")
        String orderId,

        @NotNull(message = "amount가 없습니다.")
        Long amount
) {
}
