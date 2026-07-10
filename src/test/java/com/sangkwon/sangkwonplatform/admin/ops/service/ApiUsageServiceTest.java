package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.repository.ApiUsageLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ApiUsageService 단위 테스트. 선점(reserve)의 한도 판정과 집계(record) 위임만 검증.
@ExtendWith(MockitoExtension.class)
class ApiUsageServiceTest {

    @Mock ApiUsageLogRepository apiUsageLogRepository;
    @InjectMocks ApiUsageService apiUsageService;

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void 한도_안이면_증분_후_선점이_통과한다() {
        when(apiUsageLogRepository.findTodayCallCnt("GEMINI")).thenReturn(Optional.of(5L));

        assertThatCode(() -> apiUsageService.reserve(ExternalApi.GEMINI)).doesNotThrowAnyException();

        verify(apiUsageLogRepository).increaseTodayCall("GEMINI", 1000L);
    }

    @Test
    void 한도를_넘기면_429를_던진다() {
        when(apiUsageLogRepository.findTodayCallCnt("GEMINI")).thenReturn(Optional.of(1001L));

        assertThatThrownBy(() -> apiUsageService.reserve(ExternalApi.GEMINI))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(429));
    }

    @Test
    void record는_한도_확인_없이_증분만_한다() {
        apiUsageService.record(ExternalApi.GEMINI_NEWS);

        verify(apiUsageLogRepository).increaseTodayCall("GEMINI_NEWS", 1000L);
        verify(apiUsageLogRepository, never()).findTodayCallCnt(any());
    }
}
