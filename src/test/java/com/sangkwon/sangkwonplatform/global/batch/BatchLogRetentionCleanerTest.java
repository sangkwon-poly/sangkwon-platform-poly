package com.sangkwon.sangkwonplatform.global.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 배치 이력 보존 정리: 90일 이전 이력을 지우고, 정리 실패가 예외로 새지 않는지 검증한다.
@ExtendWith(MockitoExtension.class)
class BatchLogRetentionCleanerTest {

    @Mock BatchJobLogRepository batchJobLogRepository;
    @InjectMocks BatchLogRetentionCleaner cleaner;

    @Test
    void 보존기간_90일_이전_이력을_삭제한다() {
        when(batchJobLogRepository.deleteByStartedAtBefore(org.mockito.ArgumentMatchers.any())).thenReturn(3);

        cleaner.purgeOldBatchLogs();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(batchJobLogRepository).deleteByStartedAtBefore(captor.capture());
        LocalDateTime cutoff = captor.getValue();
        // 대략 90일 전이어야 한다(89~91일 사이면 통과)
        assertThat(cutoff).isBefore(LocalDateTime.now().minusDays(89))
                .isAfter(LocalDateTime.now().minusDays(91));
    }

    @Test
    void 정리가_실패해도_예외를_전파하지_않는다() {
        when(batchJobLogRepository.deleteByStartedAtBefore(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("DB 오류"));

        cleaner.purgeOldBatchLogs(); // 예외가 새면 테스트 실패
    }
}
