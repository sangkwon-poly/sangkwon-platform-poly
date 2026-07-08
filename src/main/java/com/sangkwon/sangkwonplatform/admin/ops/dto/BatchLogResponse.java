package com.sangkwon.sangkwonplatform.admin.ops.dto;

import com.sangkwon.sangkwonplatform.global.batch.BatchJobLog;

import java.time.LocalDateTime;

public record BatchLogResponse(
        Long id,
        String jobName,
        String datasetCd,
        String stdrYyquCd,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        long processedCnt,
        long failedCnt,
        String errorMsg,
        String triggeredBy
) {
    public static BatchLogResponse from(BatchJobLog b) {
        return new BatchLogResponse(
                b.getId(), b.getJobName(), b.getDatasetCd(), b.getStdrYyquCd(),
                b.getStatus().name(), b.getStartedAt(), b.getEndedAt(),
                b.getProcessedCnt(), b.getFailedCnt(), b.getErrorMsg(), b.getTriggeredBy());
    }
}
