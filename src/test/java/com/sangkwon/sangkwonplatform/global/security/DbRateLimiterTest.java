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

// 공유 DB 레이트 리미터: 윈도 내 히트가 임계 이상이면 차단, null 키 안전, 기록은 INSERT.
class DbRateLimiterTest {

    private final JdbcTemplate jt = mock(JdbcTemplate.class);
    private final DbRateLimiter limiter = new DbRateLimiter(jt);

    @Test
    void 윈도_내_히트가_임계_이상이면_차단한다() {
        when(jt.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(10L);
        assertThat(limiter.isBlocked("k", 10, Duration.ofMinutes(30))).isTrue();
    }

    @Test
    void 윈도_내_히트가_임계_미만이면_통과한다() {
        when(jt.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(9L);
        assertThat(limiter.isBlocked("k", 10, Duration.ofMinutes(30))).isFalse();
    }

    @Test
    void 기록은_INSERT를_실행한다() {
        limiter.record("k");
        verify(jt).update(eq("INSERT INTO LOGIN_RATE_HIT (RATE_KEY, HIT_AT) VALUES (?, ?)"), eq("k"), any());
    }

    @Test
    void null_키는_기록도_차단판정도_하지_않는다() {
        assertThat(limiter.isBlocked(null, 10, Duration.ofMinutes(30))).isFalse();
        limiter.record(null);
        verify(jt, never()).update(anyString(), any(), any());
    }
}
