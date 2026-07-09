package com.sangkwon.sangkwonplatform.admin.ops.dto;

import com.sangkwon.sangkwonplatform.admin.ops.service.DatasetStats;
import com.sangkwon.sangkwonplatform.global.batch.BatchJobLog;
import com.sangkwon.sangkwonplatform.global.batch.BatchStatus;
import com.sangkwon.sangkwonplatform.global.batch.Dataset;

import java.time.LocalDateTime;

// 적재 카탈로그 한 줄: 데이터셋 메타 + 실테이블 현황(건수/적재시각/데이터 최신) + 앱 적재 실행 상태.
public record BatchCatalogResponse(
        String code,
        String label,
        String tier,
        String table,
        String cycle,
        String note,
        String sourceUrl,
        boolean appRunnable,
        // 실제 테이블에서 집계한 현황
        long recordCount,
        LocalDateTime lastLoadedAt,
        String dataPeriod,
        boolean loaded,
        Long ageDays,
        boolean stale,
        // 앱 적재 실행 상태(BATCH_JOB_LOG). 오프라인 데이터셋은 대개 null
        boolean running,
        String runStatus,
        LocalDateTime lastRunAt,
        String lastTriggeredBy
) {
    public static BatchCatalogResponse of(Dataset d, DatasetStats stats, BatchJobLog lastRun) {
        Long ageDays = stats.loadedAt() == null ? null
                : java.time.temporal.ChronoUnit.DAYS.between(stats.loadedAt().toLocalDate(), java.time.LocalDate.now());
        boolean stale = ageDays != null && ageDays > d.staleAfterDays();
        return new BatchCatalogResponse(
                d.code(), d.label(), d.tier().name(), d.table(), d.cycleLabel(),
                d.note(), d.sourceUrl(), d.appRunnable(),
                stats.count(), stats.loadedAt(), formatPeriod(d, stats.periodRaw()), stats.count() > 0,
                ageDays, stale,
                lastRun != null && lastRun.getStatus() == BatchStatus.RUNNING,
                lastRun == null ? null : lastRun.getStatus().name(),
                lastRun == null ? null : lastRun.getStartedAt(),
                lastRun == null ? null : lastRun.getTriggeredBy());
    }

    // 데이터 최신 시점을 사람이 읽는 라벨로. 분기 코드는 "2024년 3분기"처럼 편다.
    private static String formatPeriod(Dataset d, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (d.periodKind()) {
            case QUARTER -> raw.length() >= 5 ? raw.substring(0, 4) + "년 " + raw.charAt(4) + "분기" : raw;
            case YEAR -> raw + "년";
            case MONTH, DATE -> raw;
            case NONE -> null;
        };
    }
}
