package com.sangkwon.sangkwonplatform.global.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 기동 시 좀비 RUNNING 정리: 시작 후 임계를 넘겨 정체된 RUNNING만 FAILED로 내려 재적재 차단을 푼다.
// 최근 시작한 RUNNING(다른 인스턴스가 돌리는 배치)은 오탐하지 않는다.
@ExtendWith(MockitoExtension.class)
class BatchStartupRecoveryTest {

    @Mock BatchJobLogRepository batchJobLogRepository;
    BatchStartupRecovery recovery;

    private static final long STALE_MINUTES = 120L;

    @BeforeEach
    void setUp() {
        recovery = new BatchStartupRecovery(batchJobLogRepository, STALE_MINUTES);
    }

    private static BatchJobLog running(String datasetCd) {
        return BatchJobLog.start(new BatchJobSpec(datasetCd + " 적재", datasetCd, null, "admin"));
    }

    @Test
    void 정체된_RUNNING만_임계_컷오프로_조회해_FAILED로_정리한다() {
        BatchJobLog a = running("INDUSTRY_TRADEMARK");
        BatchJobLog b = running("SALES");
        when(batchJobLogRepository.findByStatusAndStartedAtBefore(eq(BatchStatus.RUNNING), any()))
                .thenReturn(List.of(a, b));

        recovery.failStaleRunning();

        assertThat(a.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(b.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(a.getErrorMsg()).contains("재시작");
        verify(batchJobLogRepository).saveAll(List.of(a, b));

        // 컷오프가 대략 임계(120분) 전이어야 최근 시작한 배치를 오탐하지 않는다
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(batchJobLogRepository).findByStatusAndStartedAtBefore(eq(BatchStatus.RUNNING), cutoff.capture());
        assertThat(cutoff.getValue())
                .isBefore(LocalDateTime.now().minusMinutes(119))
                .isAfter(LocalDateTime.now().minusMinutes(121));
    }

    @Test
    void 정체된_RUNNING이_없으면_아무것도_저장하지_않는다() {
        when(batchJobLogRepository.findByStatusAndStartedAtBefore(eq(BatchStatus.RUNNING), any()))
                .thenReturn(List.of());

        recovery.failStaleRunning();

        verify(batchJobLogRepository, never()).saveAll(any());
    }
}
