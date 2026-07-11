package com.sangkwon.sangkwonplatform.global.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

@Component
@RequiredArgsConstructor
public class BatchJobExecutor {

    private final BatchJobLogRepository batchJobLogRepository;
    private final BatchFailureNotifier batchFailureNotifier;

    // task가 실패해도 실패 이력이 남도록 트랜잭션으로 묶지 않는다
    public BatchJobLog run(BatchJobSpec spec, LongSupplier task) {
        BatchJobLog log = batchJobLogRepository.save(BatchJobLog.start(spec));
        try {
            long processed = task.getAsLong();
            log.succeed(processed);
            return batchJobLogRepository.save(log);
        } catch (Throwable e) {
            // RuntimeException뿐 아니라 Error 등 어떤 실패든 RUNNING으로 남지 않도록 이력을 FAILED로 남기고 되던진다.
            // 이력 저장까지 실패해도 원래 원인 예외를 가리지 않도록 감싸서 처리한다.
            log.fail(e.getMessage());
            try {
                batchJobLogRepository.save(log);
            } catch (Exception saveEx) {
                e.addSuppressed(saveEx);
            }
            // 실패를 외부 채널로 알린다(웹훅 미설정이면 no-op, 전송 실패는 내부에서 삼킴)
            batchFailureNotifier.notifyFailure(spec.jobName(), spec.datasetCd(), e.getMessage());
            throw e;
        }
    }
}
