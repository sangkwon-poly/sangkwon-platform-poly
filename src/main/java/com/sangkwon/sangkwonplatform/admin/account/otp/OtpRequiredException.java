package com.sangkwon.sangkwonplatform.admin.account.otp;

// 2단계 인증이 켜진 계정이 OTP 코드 없이 로그인 시도할 때. 프론트가 OTP 입력 단계로 전환하도록 신호를 준다.
public class OtpRequiredException extends RuntimeException {
}
