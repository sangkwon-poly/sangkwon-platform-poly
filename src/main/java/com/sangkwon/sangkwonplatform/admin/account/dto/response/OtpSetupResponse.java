package com.sangkwon.sangkwonplatform.admin.account.dto.response;

// OTP 설정 시작 응답. secret은 수동 입력용, otpauthUrl은 QR로 만들어 인증 앱에 등록.
public record OtpSetupResponse(
        String secret,
        String otpauthUrl
) {
}
