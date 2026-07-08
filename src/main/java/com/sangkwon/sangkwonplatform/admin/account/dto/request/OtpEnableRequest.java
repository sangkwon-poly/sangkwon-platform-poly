package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OtpEnableRequest(
        @NotBlank(message = "OTP 코드는 필수입니다!")
        String otp
) {
}
