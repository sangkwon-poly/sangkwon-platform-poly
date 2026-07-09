package com.sangkwon.sangkwonplatform.admin.trdar.service;

import com.sangkwon.sangkwonplatform.admin.trdar.dto.TrdarDetailResponse;
import com.sangkwon.sangkwonplatform.admin.trdar.dto.TrdarHealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 상권 데이터 백오피스: 품질/완결성 점검, 상권 상세 드릴다운, CSV 내보내기.
// 팩트 집계는 신뢰된 상수 테이블명으로 네이티브 쿼리한다(사용자 입력 아님).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTrdarService {

    // 완결성 점검 대상 팩트: {code, label, table}
    private static final List<String[]> FACTS = List.of(
            new String[]{"SALES", "상권 매출", "SALES"},
            new String[]{"STORE_STAT", "점포 통계", "STORE_STAT"},
            new String[]{"TRDAR_CHANGE", "상권 변화지표", "TRDAR_CHANGE"},
            new String[]{"STREET_POP", "유동인구", "STREET_POP"},
            new String[]{"RESIDENT_POP", "상주인구", "RESIDENT_POP"},
            new String[]{"ATTRACTION", "집객시설", "ATTRACTION"},
            new String[]{"APT", "아파트", "APT"});

    private final NamedParameterJdbcTemplate njt;

    public TrdarHealthResponse health(String quarter) {
        String q = resolveQuarter(quarter);
        long total = scalar("SELECT COUNT(*) FROM TRDAR", new MapSqlParameterSource());
        MapSqlParameterSource p = new MapSqlParameterSource("q", q);

        List<TrdarHealthResponse.Fact> facts = new ArrayList<>();
        for (String[] f : FACTS) {
            Map<String, Object> row = njt.queryForMap(
                    "SELECT COUNT(DISTINCT TRDAR_CD) d, COUNT(*) r FROM " + f[2] + " WHERE STDR_YYQU_CD = :q", p);
            long d = toLong(row.get("D"));
            long r = toLong(row.get("R"));
            facts.add(new TrdarHealthResponse.Fact(f[0], f[1], d, r, total > 0 ? round1(d * 100.0 / total) : 0));
        }

        TrdarHealthResponse.Flags flags = new TrdarHealthResponse.Flags(
                scalar("SELECT COUNT(DISTINCT TRDAR_CD) FROM SALES WHERE STDR_YYQU_CD = :q "
                        + "AND TRDAR_CD NOT IN (SELECT TRDAR_CD FROM TRDAR)", p),
                scalar("SELECT COUNT(*) FROM (SELECT TRDAR_CD FROM SALES WHERE STDR_YYQU_CD = :q "
                        + "GROUP BY TRDAR_CD HAVING NVL(SUM(THSMON_SELNG_AMT),0) = 0)", p),
                scalar("SELECT COUNT(*) FROM (SELECT TRDAR_CD FROM STORE_STAT WHERE STDR_YYQU_CD = :q "
                        + "GROUP BY TRDAR_CD HAVING NVL(SUM(STOR_CO),0) = 0)", p),
                scalar("SELECT COUNT(*) FROM STREET_POP WHERE STDR_YYQU_CD = :q AND NVL(TOT_FLPOP_CO,0) = 0", p));

        return new TrdarHealthResponse(q, total, facts, flags);
    }

    public TrdarDetailResponse detail(String trdarCd, String quarter) {
        String q = resolveQuarter(quarter);
        List<Map<String, Object>> info = njt.queryForList(
                "SELECT TRDAR_CD_NM nm, SIGNGU_NM gu FROM TRDAR WHERE TRDAR_CD = :cd",
                new MapSqlParameterSource("cd", trdarCd));
        if (info.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "상권을 찾을 수 없습니다");
        }

        MapSqlParameterSource p = new MapSqlParameterSource().addValue("cd", trdarCd).addValue("q", q);
        Map<String, Object> m = njt.queryForMap("""
                SELECT
                  (SELECT SUM(THSMON_SELNG_AMT) FROM SALES WHERE TRDAR_CD = :cd AND STDR_YYQU_CD = :q) salesAmt,
                  (SELECT SUM(STOR_CO)       FROM STORE_STAT WHERE TRDAR_CD = :cd AND STDR_YYQU_CD = :q) storeCnt,
                  (SELECT SUM(OPBIZ_STOR_CO) FROM STORE_STAT WHERE TRDAR_CD = :cd AND STDR_YYQU_CD = :q) openCnt,
                  (SELECT SUM(CLSBIZ_STOR_CO) FROM STORE_STAT WHERE TRDAR_CD = :cd AND STDR_YYQU_CD = :q) closeCnt,
                  (SELECT SUM(FRC_STOR_CO)   FROM STORE_STAT WHERE TRDAR_CD = :cd AND STDR_YYQU_CD = :q) frcCnt,
                  (SELECT TOT_FLPOP_CO   FROM STREET_POP   WHERE TRDAR_CD = :cd AND STDR_YYQU_CD = :q) flpop,
                  (SELECT TOT_REPOP_CO   FROM RESIDENT_POP WHERE TRDAR_CD = :cd AND STDR_YYQU_CD = :q) residentPop,
                  (SELECT TRDAR_CHNGE_IX_NM FROM TRDAR_CHANGE WHERE TRDAR_CD = :cd AND STDR_YYQU_CD = :q) changeIxNm
                FROM DUAL
                """, p);

        TrdarDetailResponse.Metrics metrics = new TrdarDetailResponse.Metrics(
                toLongN(m.get("SALESAMT")), toLongN(m.get("STORECNT")), toLongN(m.get("OPENCNT")),
                toLongN(m.get("CLOSECNT")), toLongN(m.get("FRCCNT")), toLongN(m.get("FLPOP")),
                toLongN(m.get("RESIDENTPOP")), (String) m.get("CHANGEIXNM"));

        List<TrdarDetailResponse.IndutySales> topInduty = njt.query("""
                SELECT s.INDUTY_CD cd, i.INDUTY_CD_NM nm, SUM(s.THSMON_SELNG_AMT) amt
                FROM SALES s LEFT JOIN INDUTY i ON i.INDUTY_CD = s.INDUTY_CD
                WHERE s.TRDAR_CD = :cd AND s.STDR_YYQU_CD = :q
                GROUP BY s.INDUTY_CD, i.INDUTY_CD_NM
                ORDER BY amt DESC NULLS LAST
                FETCH FIRST 8 ROWS ONLY
                """, p, (rs, i) -> new TrdarDetailResponse.IndutySales(
                rs.getString("cd"), rs.getString("nm"), getLong(rs, "amt")));

        List<TrdarDetailResponse.TrendPoint> trend = njt.query("""
                SELECT sq q,
                  (SELECT SUM(THSMON_SELNG_AMT) FROM SALES WHERE TRDAR_CD = :cd AND STDR_YYQU_CD = sq) salesAmt,
                  (SELECT SUM(STOR_CO) FROM STORE_STAT WHERE TRDAR_CD = :cd AND STDR_YYQU_CD = sq) storeCnt,
                  (SELECT TOT_FLPOP_CO FROM STREET_POP WHERE TRDAR_CD = :cd AND STDR_YYQU_CD = sq) flpop
                FROM (SELECT DISTINCT STDR_YYQU_CD sq FROM SALES) ORDER BY sq
                """, new MapSqlParameterSource("cd", trdarCd),
                (rs, i) -> new TrdarDetailResponse.TrendPoint(
                        rs.getString("q"), getLong(rs, "salesAmt"), getLong(rs, "storeCnt"), getLong(rs, "flpop")));

        return new TrdarDetailResponse(trdarCd, (String) info.get(0).get("NM"),
                (String) info.get(0).get("GU"), q, metrics, topInduty, trend);
    }

    // ── 유틸 ──────────────────────────────────────────────
    private String resolveQuarter(String quarter) {
        if (quarter != null && !quarter.isBlank()) {
            return quarter;
        }
        List<String> r = njt.getJdbcTemplate().queryForList("SELECT MAX(STDR_YYQU_CD) FROM SALES", String.class);
        String q = r.isEmpty() ? null : r.get(0);
        if (q == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "적재된 분기가 없습니다");
        }
        return q;
    }

    private long scalar(String sql, MapSqlParameterSource p) {
        Long v = njt.queryForObject(sql, p, Long.class);
        return v == null ? 0 : v;
    }

    private long toLong(Object o) {
        Long v = toLongN(o);
        return v == null ? 0 : v;
    }

    private Long toLongN(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b.longValue();
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private double round1(double x) {
        return Math.round(x * 10) / 10.0;
    }
}
