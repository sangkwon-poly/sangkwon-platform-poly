package com.sangkwon.sangkwonplatform.global.batch;

import com.sangkwon.sangkwonplatform.admin.ops.repository.ApiUsageLogRepository;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 운영 정리 스케줄러: 방치 결제 컷오프(24시간 전)와 사용량 보존 컷오프로 호출하고, 오류는 삼킨다.
@ExtendWith(MockitoExtension.class)
class OperationalCleanupSchedulerTest {

    @Mock PaymentOrderRepository paymentOrderRepository;
    @Mock ApiUsageLogRepository apiUsageLogRepository;
    @InjectMocks OperationalCleanupScheduler scheduler;

    @Test
    void 방치_PENDING을_24시간_컷오프로_FAILED_처리한다() {
        when(paymentOrderRepository.markStalePendingAsFailed(any())).thenReturn(2);

        scheduler.failStalePendingPayments();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(paymentOrderRepository).markStalePendingAsFailed(captor.capture());
        assertThat(captor.getValue())
                .isBefore(LocalDateTime.now().minusHours(23))
                .isAfter(LocalDateTime.now().minusHours(25));
    }

    @Test
    void 사용량_이력을_보존_컷오프로_삭제한다() {
        when(apiUsageLogRepository.deleteByUsageDateBefore(any())).thenReturn(10);

        scheduler.purgeOldApiUsageLogs();

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(apiUsageLogRepository).deleteByUsageDateBefore(captor.capture());
        assertThat(captor.getValue()).isBefore(LocalDate.now().minusDays(364));
    }

    @Test
    void 정리_중_오류는_삼켜서_다음_주기에_맡긴다() {
        when(paymentOrderRepository.markStalePendingAsFailed(any())).thenThrow(new RuntimeException("DB 오류"));
        when(apiUsageLogRepository.deleteByUsageDateBefore(any())).thenThrow(new RuntimeException("DB 오류"));

        assertThatCode(() -> scheduler.failStalePendingPayments()).doesNotThrowAnyException();
        assertThatCode(() -> scheduler.purgeOldApiUsageLogs()).doesNotThrowAnyException();
    }
}
