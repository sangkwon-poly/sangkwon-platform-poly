package com.sangkwon.sangkwonplatform.global.batch;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "BATCH_JOB_LOG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchJobLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "JOB_LOG_ID")
    private Long id;

    @Column(name = "JOB_NAME", nullable = false)
    private String jobName;

    @Column(name = "DATASET_CD", nullable = false)
    private String datasetCd;

    @Column(name = "STDR_YYQU_CD")
    private String stdrYyquCd;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private BatchStatus status;

    @Column(name = "STARTED_AT", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt;

    @Column(name = "PROCESSED_CNT", nullable = false)
    private long processedCnt;

    @Column(name = "FAILED_CNT", nullable = false)
    private long failedCnt;

    @Lob
    @Column(name = "ERROR_MSG")
    private String errorMsg;

    @Column(name = "TRIGGERED_BY", nullable = false)
    private String triggeredBy;

    private BatchJobLog(BatchJobSpec spec) {
        this.jobName = spec.jobName();
        this.datasetCd = spec.datasetCd();
        this.stdrYyquCd = spec.stdrYyquCd();
        this.triggeredBy = spec.triggeredBy();
        this.status = BatchStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public static BatchJobLog start(BatchJobSpec spec) {
        return new BatchJobLog(spec);
    }

    public void succeed(long processedCnt) {
        this.status = BatchStatus.SUCCESS;
        this.processedCnt = processedCnt;
        this.endedAt = LocalDateTime.now();
    }

    public void fail(String errorMsg) {
        this.status = BatchStatus.FAILED;
        this.errorMsg = errorMsg;
        this.endedAt = LocalDateTime.now();
    }
}
