package com.sangkwon.sangkwonplatform.global.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 기동 시 좀비 RUNNING 정리: 마지막 하트비트가 임계를 넘긴 행만 FAILED로 내려 재적재 차단을 푼다.
@ExtendWith(MockitoExtension.class)
class BatchStartupRecoveryTest {

    @Mock BatchJobLogRepository batchJobLogRepository;
    BatchStartupRecovery recovery;

    private static final long STALE_MINUTES = 15L;

    @BeforeEach
    void setUp() {
        recovery = new BatchStartupRecovery(batchJobLogRepository, STALE_MINUTES);
    }

    @Test
    void 하트비트가_끊긴_RUNNING만_조건부로_FAILED_처리한다() {
        when(batchJobLogRepository.failStaleRunningBefore(any(), any())).thenReturn(2);

        recovery.failStaleRunning();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(batchJobLogRepository).failStaleRunningBefore(
                cutoff.capture(), org.mockito.ArgumentMatchers.contains("재시작"));
        assertThat(cutoff.getValue())
                .isBefore(LocalDateTime.now().minusMinutes(14))
                .isAfter(LocalDateTime.now().minusMinutes(16));
        verify(batchJobLogRepository, never()).saveAll(any());
    }

    @Test
    void 하트비트가_끊긴_RUNNING이_없으면_저장하지_않는다() {
        when(batchJobLogRepository.failStaleRunningBefore(any(), any())).thenReturn(0);

        recovery.failStaleRunning();

        verify(batchJobLogRepository, never()).saveAll(any());
    }
}
