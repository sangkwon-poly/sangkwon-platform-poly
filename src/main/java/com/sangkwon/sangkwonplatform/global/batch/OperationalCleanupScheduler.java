package com.sangkwon.sangkwonplatform.global.batch;

import com.sangkwon.sangkwonplatform.admin.ops.repository.ApiUsageLogRepository;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 운영 정리 자동화: 방치된 결제(중단된 결제창)와 오래된 사용량 이력을 주기적으로 정리한다.
// 배치 이력 정리(BatchLogRetentionCleaner)와 같은 결의 베스트에포트 스케줄 작업.
// 감사 로그(AUDIT_LOG)는 컴플라이언스 대상이라 여기서 건드리지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationalCleanupScheduler {

    // 결제창만 진입하고 이 시간을 넘도록 승인 안 된 PENDING은 중단된 결제로 보고 FAILED 처리한다.
    private static final int STALE_PENDING_HOURS = 24;
    // 사용량 일자별 집계는 작지만 무한 증식하므로 이 일수만 보존한다.
    private static final int USAGE_RETENTION_DAYS = 365;

    private final PaymentOrderRepository paymentOrderRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;

    // 매시 정각. 방치 결제를 FAILED로 정리(짧은 주기라야 결제 목록·지표가 오래 어긋나지 않는다).
    @Scheduled(cron = "0 0 * * * *")
    public void failStalePendingPayments() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(STALE_PENDING_HOURS);
            int failed = paymentOrderRepository.markStalePendingAsFailed(cutoff);
            if (failed > 0) {
                log.info("방치된 결제(PENDING {}시간 초과) {}건을 FAILED로 정리", STALE_PENDING_HOURS, failed);
            }
        } catch (Exception e) {
            log.warn("방치 결제 정리 실패(다음 주기에 재시도): {}", e.getMessage());
        }
    }

    // 매일 새벽 4시 40분(KST). 오래된 사용량 이력 정리.
    @Scheduled(cron = "0 40 4 * * *")
    public void purgeOldApiUsageLogs() {
        try {
            LocalDate cutoff = LocalDate.now().minusDays(USAGE_RETENTION_DAYS);
            int deleted = apiUsageLogRepository.deleteByUsageDateBefore(cutoff);
            if (deleted > 0) {
                log.info("오래된 사용량 이력 {}건 정리(보존 {}일)", deleted, USAGE_RETENTION_DAYS);
            }
        } catch (Exception e) {
            log.warn("사용량 이력 정리 실패(다음 주기에 재시도): {}", e.getMessage());
        }
    }
}
