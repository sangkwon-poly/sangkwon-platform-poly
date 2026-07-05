package com.sangkwon.sangkwonplatform.global.batch;

public record BatchJobSpec(
        String jobName,
        String datasetCd,
        String stdrYyquCd,
        String triggeredBy
) {
}
