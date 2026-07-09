package com.sangkwon.sangkwonplatform.admin.account.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedDeviceServiceTest {

    private final TrustedDeviceService svc = new TrustedDeviceService("unit-test-secret-key", 30);

    @Test
    void 발급한_토큰은_같은_관리자에_대해_검증된다() {
        String token = svc.issue(7L);
        assertThat(svc.verify(token, 7L)).isTrue();
    }

    @Test
    void 다른_관리자의_토큰은_거부한다() {
        String token = svc.issue(7L);
        assertThat(svc.verify(token, 8L)).isFalse();
    }

    @Test
    void 변조된_토큰은_거부한다() {
        String token = svc.issue(7L);
        assertThat(svc.verify(token + "x", 7L)).isFalse();
    }

    @Test
    void 다른_키로_서명된_토큰은_거부한다() {
        String token = new TrustedDeviceService("another-secret", 30).issue(7L);
        assertThat(svc.verify(token, 7L)).isFalse();
    }

    @Test
    void 잘못된_형식이나_null은_거부한다() {
        assertThat(svc.verify(null, 7L)).isFalse();
        assertThat(svc.verify("garbage", 7L)).isFalse();
        assertThat(svc.verify("no-dot-token", 7L)).isFalse();
    }

    @Test
    void 만료된_토큰은_거부한다() {
        String expired = new TrustedDeviceService("unit-test-secret-key", -1).issue(7L);
        assertThat(svc.verify(expired, 7L)).isFalse();
    }
}
