package com.sangkwon.sangkwonplatform.member.dto.request;

import jakarta.validation.constraints.NotBlank;

// 결제 주문 생성 요청. 금액은 받지 않는다: 플랜·주기로 서버가 산정한다.
public record PaymentOrderCreateRequest(
        @NotBlank(message = "플랜을 선택해 주세요.")
        String plan,

        @NotBlank(message = "결제 주기를 선택해 주세요.")
        String billingCycle
) {
}
