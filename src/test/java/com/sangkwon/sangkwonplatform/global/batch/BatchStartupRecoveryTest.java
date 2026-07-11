package com.sangkwon.sangkwonplatform.global.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 기동 시 좀비 RUNNING 정리: 재시작으로 갱신되지 못한 RUNNING을 FAILED로 내려 재적재 차단을 푼다.
@ExtendWith(MockitoExtension.class)
class BatchStartupRecoveryTest {

    @Mock BatchJobLogRepository batchJobLogRepository;
    @InjectMocks BatchStartupRecovery recovery;

    private static BatchJobLog running(String datasetCd) {
        return BatchJobLog.start(new BatchJobSpec(datasetCd + " 적재", datasetCd, null, "admin"));
    }

    @Test
    void 기동_시_남아있는_RUNNING을_FAILED로_정리한다() {
        BatchJobLog a = running("INDUSTRY_TRADEMARK");
        BatchJobLog b = running("SALES");
        when(batchJobLogRepository.findByStatus(BatchStatus.RUNNING)).thenReturn(List.of(a, b));

        recovery.failStaleRunning();

        assertThat(a.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(b.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(a.getErrorMsg()).contains("재시작");
        verify(batchJobLogRepository).saveAll(List.of(a, b));
    }

    @Test
    void 남은_RUNNING이_없으면_아무것도_저장하지_않는다() {
        when(batchJobLogRepository.findByStatus(BatchStatus.RUNNING)).thenReturn(List.of());

        recovery.failStaleRunning();

        verify(batchJobLogRepository, never()).saveAll(any());
    }
}
