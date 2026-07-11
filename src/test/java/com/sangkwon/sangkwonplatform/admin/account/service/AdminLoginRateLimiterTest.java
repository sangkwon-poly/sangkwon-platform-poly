package com.sangkwon.sangkwonplatform.admin.account.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 관리자 로그인 IP 레이트 리미터: 임계(10) 이상 차단, 성공 시 리셋, IP별 독립, null 안전.
class AdminLoginRateLimiterTest {

    @Test
    void 임계_미만은_통과하고_임계_이상은_차단한다() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter();
        for (int i = 0; i < 9; i++) {
            limiter.recordFailure("1.2.3.4");
        }
        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();

        limiter.recordFailure("1.2.3.4"); // 10회
        assertThat(limiter.isBlocked("1.2.3.4")).isTrue();
    }

    @Test
    void 리셋하면_차단이_풀린다() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter();
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure("1.2.3.4");
        }
        assertThat(limiter.isBlocked("1.2.3.4")).isTrue();

        limiter.reset("1.2.3.4");
        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    void IP별로_독립적으로_집계한다() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter();
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure("1.1.1.1");
        }
        assertThat(limiter.isBlocked("1.1.1.1")).isTrue();
        assertThat(limiter.isBlocked("2.2.2.2")).isFalse();
    }

    @Test
    void null_IP는_차단하지_않고_기록도_안전하다() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter();
        limiter.recordFailure(null);
        limiter.reset(null);
        assertThat(limiter.isBlocked(null)).isFalse();
    }
}
