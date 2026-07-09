package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.global.batch.Dataset;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

// 데이터셋의 실제 적재 현황을 대상 테이블에서 직접 집계한다.
// 테이블/컬럼명은 Dataset enum의 신뢰된 상수라 네이티브 SQL에 그대로 끼워넣는다(사용자 입력 아님).
@Component
public class DatasetStatsReader {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public DatasetStats read(Dataset d) {
        String sql = "SELECT COUNT(*), MAX(CREATED_AT), " + periodExpr(d) + " FROM " + d.table();
        Object[] row = (Object[]) em.createNativeQuery(sql).getSingleResult();

        long count = (row[0] == null) ? 0 : ((Number) row[0]).longValue();
        LocalDateTime loadedAt = toLocalDateTime(row[1]);
        String period = (row[2] == null) ? null : row[2].toString().trim();
        return new DatasetStats(count, loadedAt, period);
    }

    // 기간 컬럼이 없으면 NULL, 날짜형은 YYYY-MM-DD 문자열, 그 외(분기/연월/연도)는 원문 그대로 문자열로.
    private String periodExpr(Dataset d) {
        if (d.periodCol() == null) {
            return "CAST(NULL AS VARCHAR2(30))";
        }
        return d.periodKind() == Dataset.PeriodKind.DATE
                ? "TO_CHAR(MAX(" + d.periodCol() + "), 'YYYY-MM-DD')"
                : "TO_CHAR(MAX(" + d.periodCol() + "))";
    }

    private LocalDateTime toLocalDateTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        return null;
    }
}
