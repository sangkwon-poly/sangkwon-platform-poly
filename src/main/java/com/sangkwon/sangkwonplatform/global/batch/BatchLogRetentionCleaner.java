package com.sangkwon.sangkwonplatform.global.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 배치 실행 이력 보존 정리. 배치가 돌 때마다 쌓여 무한 증식하므로 오래된 이력을 주기적으로 지운다.
// 감사 로그(AUDIT_LOG)는 컴플라이언스 대상이라 여기서 건드리지 않는다(보존 정책은 팀 결정).
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchLogRetentionCleaner {

    private static final int RETENTION_DAYS = 90;

    private final BatchJobLogRepository batchJobLogRepository;

    // 매일 새벽 4시(KST). 정리는 베스트에포트라 실패해도 다음 주기에 다시 시도한다.
    @Scheduled(cron = "0 0 4 * * *")
    @SchedulerLock(name = "purgeOldBatchLogs", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void purgeOldBatchLogs() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
            int deleted = batchJobLogRepository.deleteByStartedAtBefore(cutoff);
            if (deleted > 0) {
                log.info("오래된 배치 이력 {}건 정리(보존 {}일)", deleted, RETENTION_DAYS);
            }
        } catch (Exception e) {
            log.warn("배치 이력 정리 실패(다음 주기에 재시도): {}", e.getMessage());
        }
    }
}
