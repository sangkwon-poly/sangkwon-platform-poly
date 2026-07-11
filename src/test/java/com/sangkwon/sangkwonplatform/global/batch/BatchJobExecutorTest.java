package com.sangkwon.sangkwonplatform.global.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchJobExecutorTest {

    @Mock
    BatchJobLogRepository batchJobLogRepository;

    @Mock
    BatchFailureNotifier batchFailureNotifier;

    @InjectMocks
    BatchJobExecutor batchJobExecutor;

    private final BatchJobSpec spec = new BatchJobSpec("매출 적재", "SALES", "20241", "MANUAL");

    @Test
    void 성공하면_SUCCESS와_처리건수를_기록한다() {
        when(batchJobLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchJobLog result = batchJobExecutor.run(spec, () -> 100L);

        assertThat(result.getStatus()).isEqualTo(BatchStatus.SUCCESS);
        assertThat(result.getProcessedCnt()).isEqualTo(100L);
        assertThat(result.getEndedAt()).isNotNull();
    }

    @Test
    void 실패하면_FAILED와_에러메시지를_남기고_예외를_전파한다() {
        when(batchJobLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> batchJobExecutor.run(spec, () -> {
            throw new IllegalStateException("연동 실패");
        })).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<BatchJobLog> captor = ArgumentCaptor.forClass(BatchJobLog.class);
        verify(batchJobLogRepository, atLeastOnce()).save(captor.capture());
        BatchJobLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(saved.getErrorMsg()).isEqualTo("연동 실패");
        // 실패는 외부 알림 채널로도 밀어준다
        verify(batchFailureNotifier).notifyFailure("매출 적재", "SALES", "연동 실패");
    }

    @Test
    void 성공하면_실패_알림을_보내지_않는다() {
        when(batchJobLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        batchJobExecutor.run(spec, () -> 10L);

        org.mockito.Mockito.verifyNoInteractions(batchFailureNotifier);
    }
}
