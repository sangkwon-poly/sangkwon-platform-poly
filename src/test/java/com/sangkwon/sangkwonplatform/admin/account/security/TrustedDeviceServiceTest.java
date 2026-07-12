package com.sangkwon.sangkwonplatform.admin.account.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedDeviceServiceTest {

    // 시크릿을 명시하는 테스트에서는 프로파일이 쓰이지 않으므로 빈 환경이면 충분하다.
    private static MockEnvironment noProfile() {
        return new MockEnvironment();
    }

    private final TrustedDeviceService svc = new TrustedDeviceService("unit-test-secret-key", 30, noProfile());
    private static final String OTP_SECRET = "GEZDGNBVGY3TQOJQ";

    @Test
    void 발급한_토큰은_같은_관리자에_대해_검증된다() {
        String token = svc.issue(7L, 1, OTP_SECRET);
        assertThat(svc.verify(token, 7L, 1, OTP_SECRET)).isTrue();
    }

    @Test
    void 다른_관리자의_토큰은_거부한다() {
        String token = svc.issue(7L, 1, OTP_SECRET);
        assertThat(svc.verify(token, 8L, 1, OTP_SECRET)).isFalse();
    }

    @Test
    void 변조된_토큰은_거부한다() {
        String token = svc.issue(7L, 1, OTP_SECRET);
        assertThat(svc.verify(token + "x", 7L, 1, OTP_SECRET)).isFalse();
    }

    @Test
    void 다른_키로_서명된_토큰은_거부한다() {
        String token = new TrustedDeviceService("another-secret", 30, noProfile())
                .issue(7L, 1, OTP_SECRET);
        assertThat(svc.verify(token, 7L, 1, OTP_SECRET)).isFalse();
    }

    @Test
    void 잘못된_형식이나_null은_거부한다() {
        assertThat(svc.verify(null, 7L, 1, OTP_SECRET)).isFalse();
        assertThat(svc.verify("garbage", 7L, 1, OTP_SECRET)).isFalse();
        assertThat(svc.verify("no-dot-token", 7L, 1, OTP_SECRET)).isFalse();
    }

    @Test
    void 만료된_토큰은_거부한다() {
        String expired = new TrustedDeviceService("unit-test-secret-key", -1, noProfile())
                .issue(7L, 1, OTP_SECRET);
        assertThat(svc.verify(expired, 7L, 1, OTP_SECRET)).isFalse();
    }

    @Test
    void 비밀번호나_OTP_비밀키가_바뀌면_기존_토큰을_거부한다() {
        String token = svc.issue(7L, 1, OTP_SECRET);

        assertThat(svc.verify(token, 7L, 2, OTP_SECRET)).isFalse();
        assertThat(svc.verify(token, 7L, 1, "NEWOTPSECRET")).isFalse();
    }

    @Test
    void OTP_비밀키가_없으면_토큰을_발급하지_않는다() {
        assertThatThrownBy(() -> svc.issue(7L, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void prod에_시크릿이_없으면_기동에_실패한다() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        // 인스턴스마다 랜덤 키가 생기는 것을 prod에서 금지한다(다중 인스턴스 신뢰기기 일관성)
        assertThatThrownBy(() -> new TrustedDeviceService("", 30, prod))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prod가_아니면_시크릿이_없어도_랜덤키로_기동한다() {
        String token = new TrustedDeviceService("", 30, noProfile()).issue(1L, 0, OTP_SECRET);
        assertThat(token).isNotBlank();
    }
}
