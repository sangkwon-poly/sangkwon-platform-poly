package com.sangkwon.sangkwonplatform.global.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 기동 시 좀비 RUNNING 정리. BATCH_JOB_LOG의 RUNNING은 프로세스 생존 중에만 유효한데,
// 재배포/크래시로 프로세스가 죽으면 갱신되지 못하고 남아 해당 데이터셋 재적재를 영구 차단한다.
// 정체(staleness) 기준으로 좁힌다: 시작 후 임계(기본 2시간, 정상 배치 최대 실행시간보다 넉넉)를 넘긴 RUNNING만
// 좀비로 본다. 이래야 다중 인스턴스/롤링 배포에서 다른 인스턴스가 지금 돌리는 배치를 오탐해 FAILED로 덮지 않는다.
// (임계 안의 최근 좀비는 관리자 수동 초기화 엔드포인트로 즉시 풀 수 있다.)
@Slf4j
@Component
public class BatchStartupRecovery {

    private final BatchJobLogRepository batchJobLogRepository;
    private final Duration staleThreshold;

    public BatchStartupRecovery(BatchJobLogRepository batchJobLogRepository,
                                @Value("${batch.startup-recovery.stale-after-minutes:120}") long staleAfterMinutes) {
        this.batchJobLogRepository = batchJobLogRepository;
        this.staleThreshold = Duration.ofMinutes(staleAfterMinutes);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failStaleRunning() {
        // 정리는 베스트에포트다. DB 접근이 실패해도 기동 자체를 막지 않는다(수동 초기화 엔드포인트가 대체 경로).
        try {
            LocalDateTime cutoff = LocalDateTime.now().minus(staleThreshold);
            List<BatchJobLog> stale = batchJobLogRepository.findByStatusAndStartedAtBefore(BatchStatus.RUNNING, cutoff);
            if (stale.isEmpty()) {
                return;
            }
            stale.forEach(log -> log.fail("서버 재시작으로 중단됨(자동 정리)"));
            batchJobLogRepository.saveAll(stale);
            log.info("정체된 RUNNING 배치 {}건을 FAILED로 정리(임계 {}분 초과)", stale.size(), staleThreshold.toMinutes());
        } catch (Exception e) {
            log.warn("배치 좀비 정리 건너뜀(기동은 계속): {}", e.getMessage());
        }
    }
}
