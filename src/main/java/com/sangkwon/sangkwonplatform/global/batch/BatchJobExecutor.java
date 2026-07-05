package com.sangkwon.sangkwonplatform.global.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

@Component
@RequiredArgsConstructor
public class BatchJobExecutor {

    private final BatchJobLogRepository batchJobLogRepository;

    // task가 실패해도 실패 이력이 남도록 트랜잭션으로 묶지 않는다
    public BatchJobLog run(BatchJobSpec spec, LongSupplier task) {
        BatchJobLog log = batchJobLogRepository.save(BatchJobLog.start(spec));
        try {
            long processed = task.getAsLong();
            log.succeed(processed);
            return batchJobLogRepository.save(log);
        } catch (RuntimeException e) {
            log.fail(e.getMessage());
            batchJobLogRepository.save(log);
            throw e;
        }
    }
}
