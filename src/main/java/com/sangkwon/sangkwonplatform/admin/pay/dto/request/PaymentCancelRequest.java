package com.sangkwon.sangkwonplatform.admin.pay.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 관리자 환불 요청. 사유는 토스 결제취소 API의 cancelReason으로 전달되고 감사 로그에도 남는다.
public record PaymentCancelRequest(
        @NotBlank(message = "환불 사유는 필수입니다.")
        @Size(max = 200, message = "환불 사유는 200자 이내여야 합니다.")
        String reason
) {
}
