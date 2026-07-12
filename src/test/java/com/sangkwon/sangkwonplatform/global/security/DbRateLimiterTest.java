package com.sangkwon.sangkwonplatform.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 공유 DB 레이트 리미터: 슬롯 선점 후 임계 이하면 통과, 초과면 거절, null 키는 무시.
class DbRateLimiterTest {

    private final JdbcTemplate jt = mock(JdbcTemplate.class);
    private final DbRateLimiter limiter = new DbRateLimiter(jt);

    @Test
    void 슬롯_선점_후_임계_이하면_통과한다() {
        when(jt.queryForObject(anyString(), eq(Integer.class), eq("k"))).thenReturn(5);

        assertThat(limiter.tryAcquire("k", 10, Duration.ofMinutes(30))).isTrue();
        verify(jt).update(anyString(), eq("k"), any(), any());
    }

    @Test
    void 슬롯_선점_후_임계_초과면_거절한다() {
        when(jt.queryForObject(anyString(), eq(Integer.class), eq("k"))).thenReturn(11);

        assertThat(limiter.tryAcquire("k", 10, Duration.ofMinutes(30))).isFalse();
    }

    @Test
    void null_키는_항상_통과한다() {
        assertThat(limiter.tryAcquire(null, 10, Duration.ofMinutes(30))).isTrue();
        verify(jt, never()).update(anyString(), any(), any(), any());
    }
}
