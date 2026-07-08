package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
        @NotBlank(message = "로그인 ID는 필수입니다!")
        String loginId,
        @NotBlank(message = "비밀번호는 필수입니다!")
        String password,
        // 2단계 인증(OTP)을 켠 계정만 사용. 안 켠 계정은 비워도 된다.
        String otp,
        // "이 기기 신뢰" 체크 여부. OTP 통과 후 신뢰 쿠키를 발급한다.
        boolean trustDevice
) {
}
