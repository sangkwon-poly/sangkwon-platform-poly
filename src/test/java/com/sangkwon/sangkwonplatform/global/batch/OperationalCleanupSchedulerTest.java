package com.sangkwon.sangkwonplatform.global.batch;

import com.sangkwon.sangkwonplatform.admin.ops.repository.ApiUsageLogRepository;
import com.sangkwon.sangkwonplatform.admin.pay.dto.response.AdminPaymentResponse;
import com.sangkwon.sangkwonplatform.admin.pay.service.AdminPaymentService;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 운영 정리 스케줄러: 방치 결제는 토스와 재확인(대사)해 복구/실패 처리하고, 사용량 이력은 보존 컷오프로 지운다.
// 오류(대상 조회 실패, 개별 대사 실패)는 삼켜 다음 주기에 맡긴다.
@ExtendWith(MockitoExtension.class)
class OperationalCleanupSchedulerTest {

    @Mock PaymentOrderRepository paymentOrderRepository;
    @Mock AdminPaymentService adminPaymentService;
    @Mock ApiUsageLogRepository apiUsageLogRepository;
    @Mock com.sangkwon.sangkwonplatform.global.security.DbRateLimiter dbRateLimiter;
    @Mock com.sangkwon.sangkwonplatform.map.service.AiReportQuota aiReportQuota;
    @Mock com.sangkwon.sangkwonplatform.member.service.SearchLogService searchLogService;
    @InjectMocks OperationalCleanupScheduler scheduler;

    private static AdminPaymentService.ReconcileResult result(PaymentStatus before, PaymentStatus after) {
        return new AdminPaymentService.ReconcileResult(before, after, (AdminPaymentResponse) null);
    }

    @Test
    void 방치_PENDING을_24시간_컷오프로_토스와_대사한다() {
        when(paymentOrderRepository.findStalePendingOrderIds(any(), any())).thenReturn(List.of("o1", "o2"));
        // o1은 실제 결제였음(유실 복구), o2는 진짜 방치(실패 확정)
        when(adminPaymentService.reconcile("o1")).thenReturn(result(PaymentStatus.PENDING, PaymentStatus.PAID));
        when(adminPaymentService.reconcile("o2")).thenReturn(result(PaymentStatus.PENDING, PaymentStatus.FAILED));

        scheduler.reconcileStalePendingPayments();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(paymentOrderRepository).findStalePendingOrderIds(cutoff.capture(), any());
        assertThat(cutoff.getValue())
                .isBefore(LocalDateTime.now().minusHours(23))
                .isAfter(LocalDateTime.now().minusHours(25));
        verify(adminPaymentService).reconcile("o1");
        verify(adminPaymentService).reconcile("o2");
    }

    @Test
    void 대사_대상이_없으면_아무것도_하지_않는다() {
        when(paymentOrderRepository.findStalePendingOrderIds(any(), any())).thenReturn(List.of());

        scheduler.reconcileStalePendingPayments();

        verify(adminPaymentService, never()).reconcile(any());
    }

    @Test
    void 개별_주문_대사_실패는_삼켜서_나머지를_계속_대사한다() {
        when(paymentOrderRepository.findStalePendingOrderIds(any(), any())).thenReturn(List.of("o1", "o2"));
        // o1 토스 조회 실패(확인 안 된 결제라 실패로 단정하지 않고 다음 주기로) → o2는 계속 처리돼야 한다
        when(adminPaymentService.reconcile("o1")).thenThrow(new RuntimeException("토스 조회 실패"));
        when(adminPaymentService.reconcile("o2")).thenReturn(result(PaymentStatus.PENDING, PaymentStatus.FAILED));

        assertThatCode(() -> scheduler.reconcileStalePendingPayments()).doesNotThrowAnyException();

        verify(adminPaymentService).reconcile("o2");
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
        when(paymentOrderRepository.findStalePendingOrderIds(any(), any())).thenThrow(new RuntimeException("DB 오류"));
        when(apiUsageLogRepository.deleteByUsageDateBefore(any())).thenThrow(new RuntimeException("DB 오류"));

        assertThatCode(() -> scheduler.reconcileStalePendingPayments()).doesNotThrowAnyException();
        assertThatCode(() -> scheduler.purgeOldApiUsageLogs()).doesNotThrowAnyException();
    }
}
