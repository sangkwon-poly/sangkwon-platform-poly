package com.sangkwon.sangkwonplatform.global.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Slf4j
@Component
public class BatchJobExecutor {

    private final BatchJobLogRepository batchJobLogRepository;
    private final BatchFailureNotifier batchFailureNotifier;
    private final ScheduledExecutorService batchHeartbeatScheduler;
    private final long heartbeatIntervalSeconds;

    public BatchJobExecutor(BatchJobLogRepository batchJobLogRepository,
                            BatchFailureNotifier batchFailureNotifier,
                            @Qualifier("batchHeartbeatScheduler") ScheduledExecutorService batchHeartbeatScheduler,
                            @Value("${batch.running-heartbeat.interval-seconds:300}") long heartbeatIntervalSeconds) {
        this.batchJobLogRepository = batchJobLogRepository;
        this.batchFailureNotifier = batchFailureNotifier;
        this.batchHeartbeatScheduler = batchHeartbeatScheduler;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    // task가 실패해도 실패 이력이 남도록 트랜잭션으로 묶지 않는다.
    // null 반환: 같은 데이터셋이 이미 다른 인스턴스/트리거에서 RUNNING이라 이번 실행을 건너뛴 경우.
    public BatchJobLog run(BatchJobSpec spec, LongSupplier task) {
        BatchJobLog jobLog;
        try {
            jobLog = batchJobLogRepository.save(BatchJobLog.start(spec));
        } catch (DataIntegrityViolationException dup) {
            // 부분 유니크 인덱스(UX_BATCH_JOB_RUNNING) 위반 = 다른 인스턴스/트리거가 같은 데이터셋을 이미 RUNNING으로
            // 시작했다는 뜻. 크로스 인스턴스 중복 적재(같은 테이블 동시 DELETE+INSERT)를 DB에서 원자적으로 막고 건너뛴다.
            log.warn("적재 건너뜀: {}는 이미 다른 실행이 진행 중입니다(RUNNING 유니크 충돌)", spec.datasetCd());
            return null;
        }
        ScheduledFuture<?> heartbeat = startHeartbeat(jobLog);
        try {
            long processed = task.getAsLong();
            jobLog.succeed(processed);
            return batchJobLogRepository.save(jobLog);
        } catch (Throwable e) {
            // RuntimeException뿐 아니라 Error 등 어떤 실패든 RUNNING으로 남지 않도록 이력을 FAILED로 남기고 되던진다.
            // 이력 저장까지 실패해도 원래 원인 예외를 가리지 않도록 감싸서 처리한다.
            jobLog.fail(e.getMessage());
            try {
                batchJobLogRepository.save(jobLog);
            } catch (Exception saveEx) {
                e.addSuppressed(saveEx);
            }
            // 실패를 외부 채널로 알린다(웹훅 미설정이면 no-op, 전송 실패는 내부에서 삼킴)
            batchFailureNotifier.notifyFailure(spec.jobName(), spec.datasetCd(), e.getMessage());
            throw e;
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
        }
    }

    private ScheduledFuture<?> startHeartbeat(BatchJobLog jobLog) {
        try {
            return batchHeartbeatScheduler.scheduleAtFixedRate(
                    () -> touchHeartbeat(jobLog),
                    heartbeatIntervalSeconds,
                    heartbeatIntervalSeconds,
                    TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("배치 하트비트 시작 실패: jobLogId={}, 원인={}", jobLog.getId(), e.getMessage());
            return null;
        }
    }

    private void touchHeartbeat(BatchJobLog jobLog) {
        try {
            batchJobLogRepository.touchRunningHeartbeat(jobLog.getId());
        } catch (RuntimeException e) {
            // 일시적인 하트비트 실패 때문에 실제 적재까지 중단하지 않는다.
            log.warn("배치 하트비트 갱신 실패: jobLogId={}, 원인={}", jobLog.getId(), e.getMessage());
        }
    }
}
