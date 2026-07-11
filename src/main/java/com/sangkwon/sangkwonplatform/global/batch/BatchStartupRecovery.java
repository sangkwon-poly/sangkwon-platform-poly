package com.sangkwon.sangkwonplatform.global.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

// 기동 시 좀비 RUNNING 정리. BATCH_JOB_LOG의 RUNNING은 프로세스 생존 중에만 유효한데,
// 재배포/크래시로 프로세스가 죽으면 갱신되지 못하고 남아 해당 데이터셋 재적재를 영구 차단한다.
// 단일 인스턴스 가정: 기동 시점에는 실제로 실행 중인 배치가 없으므로 남은 RUNNING은 모두 중단된 좀비다.
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchStartupRecovery {

    private final BatchJobLogRepository batchJobLogRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void failStaleRunning() {
        // 정리는 베스트에포트다. DB 접근이 실패해도 기동 자체를 막지 않는다(수동 초기화 엔드포인트가 대체 경로).
        try {
            List<BatchJobLog> stale = batchJobLogRepository.findByStatus(BatchStatus.RUNNING);
            if (stale.isEmpty()) {
                return;
            }
            stale.forEach(log -> log.fail("서버 재시작으로 중단됨(자동 정리)"));
            batchJobLogRepository.saveAll(stale);
        } catch (Exception e) {
            log.warn("배치 좀비 정리 건너뜀(기동은 계속): {}", e.getMessage());
        }
    }
}
