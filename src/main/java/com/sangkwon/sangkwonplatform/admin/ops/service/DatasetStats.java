package com.sangkwon.sangkwonplatform.admin.ops.service;

import java.time.LocalDateTime;

// 실제 테이블에서 읽은 적재 현황: 레코드 수, 마지막 적재 시각(MAX CREATED_AT), 데이터 최신 시점(원문)
public record DatasetStats(long count, LocalDateTime loadedAt, String periodRaw) {
    public static DatasetStats empty() {
        return new DatasetStats(0, null, null);
    }
}
