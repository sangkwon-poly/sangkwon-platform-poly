package com.sangkwon.sangkwonplatform.admin.account.service;

import com.sangkwon.sangkwonplatform.global.security.DbRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 관리자 로그인 IP 레이트리밋: 스코프 키·임계(10)·윈도(30분)로 공유 DbRateLimiter에 위임하는지 검증.
class AdminLoginRateLimiterTest {

    private final DbRateLimiter db = mock(DbRateLimiter.class);
    private final AdminLoginRateLimiter limiter = new AdminLoginRateLimiter(db);

    @Test
    void 임계_10회_30분_윈도로_스코프_키에_차단을_위임한다() {
        when(db.isBlocked("admin-login:1.2.3.4", 10, Duration.ofMinutes(30))).thenReturn(true);
        assertThat(limiter.isBlocked("1.2.3.4")).isTrue();
    }

    @Test
    void 실패는_스코프_키로_기록한다() {
        limiter.recordFailure("1.2.3.4");
        verify(db).record("admin-login:1.2.3.4");
    }

    @Test
    void null_IP는_기록하지_않는다() {
        limiter.recordFailure(null);
        verify(db, never()).record(any());
    }
}
