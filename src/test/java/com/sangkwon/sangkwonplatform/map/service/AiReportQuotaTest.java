package com.sangkwon.sangkwonplatform.map.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 회원별 AI 리포트 한도의 원자 선점 판정을 검증한다. 카운터 값은 MERGE 뒤 조회(bumpAndCount)의 결과를 순서대로 스텁한다.
class AiReportQuotaTest {

    private final JdbcTemplate jt = mock(JdbcTemplate.class);
    private final AiReportQuota quota = new AiReportQuota(jt);

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void 무료_회원_시간당_월_모두_이내면_통과한다() {
        // 시간당 조회=1, 월 조회=1 (둘 다 한도 이내)
        when(jt.queryForObject(anyString(), eq(Long.class), any())).thenReturn(1L, 1L);
        assertThatCode(() -> quota.reserve(1L, false, 20, 3)).doesNotThrowAnyException();
        // 시간당·월 슬롯을 각각 +1 (MERGE 두 번)
        verify(jt, times(2)).queryForObject(anyString(), eq(Long.class), any());
    }

    @Test
    void 무료_회원_월_한도_초과면_402를_던진다() {
        // 시간당=3(이내), 월=4(>3 초과)
        when(jt.queryForObject(anyString(), eq(Long.class), any())).thenReturn(3L, 4L);
        assertThatThrownBy(() -> quota.reserve(1L, false, 20, 3))
                .satisfies(e -> assertThat(status(e)).isEqualTo(402));
    }

    @Test
    void 시간당_한도_초과면_429를_던지고_월_슬롯은_보지_않는다() {
        // 첫 조회(시간당)=21(>20) → 월 슬롯은 건드리지 않고 즉시 429
        when(jt.queryForObject(anyString(), eq(Long.class), any())).thenReturn(21L);
        assertThatThrownBy(() -> quota.reserve(1L, false, 20, 3))
                .satisfies(e -> assertThat(status(e)).isEqualTo(429));
        // 시간당에서 막혀 카운터 조회는 한 번(월 키는 보지 않는다)
        verify(jt, times(1)).queryForObject(anyString(), eq(Long.class), any());
    }

    @Test
    void Pro_회원은_월_슬롯을_보지_않는다() {
        // Pro는 월 한도 미적용: 시간당만 확인
        when(jt.queryForObject(anyString(), eq(Long.class), any())).thenReturn(1L);
        assertThatCode(() -> quota.reserve(9L, true, 20, 3)).doesNotThrowAnyException();
        verify(jt, times(1)).queryForObject(anyString(), eq(Long.class), any());
    }
}
