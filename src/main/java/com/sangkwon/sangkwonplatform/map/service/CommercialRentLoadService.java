package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.service.ApiUsageService;
import com.sangkwon.sangkwonplatform.global.config.LoaderHttp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 한국부동산원(R-ONE) 상업용부동산 임대동향 -> COMMERCIAL_RENT 적재를 앱에서 실행한다. 파이썬 05 포팅.
// 통계표 목록을 이름으로 선별(임대료/공실률/수익률/가격지수 x 오피스/중대형/소규모/집합)해 분기 데이터를 모은다.
// DELETE 후 전체 재적재라 트랜잭션으로 묶으면 멱등하다. DIM_QUARTER에 있는 분기만 남긴다.
@Slf4j
@Service
public class CommercialRentLoadService {

    private static final String BASE = "https://www.reb.or.kr/r-one/openapi";
    private static final int MAX_PAGE = 200;
    private static final List<String> PERIODS = List.of("(2019년)", "(2020년)", "(2021년)", "(2022년~)");
    // STATBL_NM 접미사 -> RLST_TY_CD
    private static final Map<String, String> TYPES = Map.of(
            "오피스", "오피스", "중대형 상가", "중대형상가", "소규모 상가", "소규모상가", "집합 상가", "집합상가");

    private final RestTemplate rest = LoaderHttp.timed();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jt;
    private final ApiUsageService apiUsageService;

    @Value("${reb.rone.key:}")
    private String rebKey;

    public CommercialRentLoadService(JdbcTemplate jt, ApiUsageService apiUsageService) {
        this.jt = jt;
        this.apiUsageService = apiUsageService;
    }

    @Transactional
    public long load() {
        Set<String> quarters = new HashSet<>(jt.queryForList("SELECT STDR_YYQU_CD FROM DIM_QUARTER", String.class));
        List<Target> targets = listTargets();

        // (region, type, metric, quarter) -> row (중복 제거)
        Map<String, Object[]> rows = new LinkedHashMap<>();
        for (Target t : targets) {
            for (JsonNode r : fetchData(t.statblId())) {
                String qc = qcode(text(r, "WRTTIME_IDTFR_ID"));
                if (qc == null || !quarters.contains(qc)) {
                    continue;
                }
                String val = text(r, "DTA_VAL");
                String reg = text(r, "CLS_ID");
                if (val == null || reg == null) {
                    continue;
                }
                Double value = numOrNull(val);
                if (value == null) {
                    continue;
                }
                String regnm = clip(firstNonNull(text(r, "CLS_FULLNM"), text(r, "CLS_NM"), reg), 200);
                String uom = clip(text(r, "UI_NM"), 20);
                String key = reg + "|" + t.type() + "|" + t.metricCd() + "|" + qc;
                rows.put(key, new Object[]{clip(reg, 20), regnm, t.type(), t.metricCd(), t.metricNm(), qc, value, uom});
            }
        }

        // 원천 장애(R-ONE HTTP 200 빈/오류 응답, 통계표명 포맷 변경 등)로 수집이 비면 기존 적재분을
        // 지우지 않는다. COMMERCIAL_RENT는 업종·상권 동향 화면에 직접 노출되므로 통삭제 시 화면이 빈다.
        if (rows.isEmpty()) {
            return 0;
        }
        jt.update("DELETE FROM COMMERCIAL_RENT");
        jt.batchUpdate("INSERT INTO COMMERCIAL_RENT (REGION_CD,REGION_NM,RLST_TY_CD,METRIC_CD,METRIC_NM,"
                + "STDR_YYQU_CD,METRIC_VALUE,UOM) VALUES (?,?,?,?,?,?,?,?)", new ArrayList<>(rows.values()));
        return rows.size();
    }

    // 통계표 목록에서 대상만 선별
    private List<Target> listTargets() {
        List<Target> out = new ArrayList<>();
        for (int p = 1; p <= 4; p++) {
            List<JsonNode> rr = extractRows(api("SttsApiTbl.do", "pIndex=" + p + "&pSize=200"));
            if (rr.isEmpty()) {
                break;
            }
            for (JsonNode r : rr) {
                Target t = classify(text(r, "STATBL_ID"), text(r, "STATBL_NM"));
                if (t != null) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    private List<JsonNode> fetchData(String statblId) {
        List<JsonNode> all = new ArrayList<>();
        int total = -1;
        for (int page = 1; page <= MAX_PAGE; page++) {
            JsonNode root = api("SttsApiTblData.do",
                    "STATBL_ID=" + enc(statblId) + "&DTACYCLE_CD=QY&pIndex=" + page + "&pSize=1000");
            if (total < 0) {
                total = roneTotalCount(root);
            }
            List<JsonNode> chunk = extractRows(root);
            if (chunk.isEmpty()) {
                break;
            }
            all.addAll(chunk);
            if (chunk.size() < 1000) {
                break;
            }
        }
        // 완결성 검사: R-ONE가 알려준 총건수(list_total_count)보다 적게 받았으면 페이지 중간 빈/오류 응답으로
        // 끊긴 부분 시계열이다. 커밋하면 최신 분기가 화면에서 사라지므로 예외로 load()의 @Transactional을 롤백한다.
        // (list_total_count가 없으면 검사를 생략해 오탐을 만들지 않는다.)
        if (total > 0 && all.size() < total) {
            throw new IllegalStateException("COMMERCIAL_RENT 적재 미완(" + statblId + "): 기대 "
                    + total + "행 중 " + all.size() + "행만 수신(부분 스냅샷 방지, 롤백)");
        }
        return all;
    }

    // R-ONE 응답 어딘가의 list_total_count(전체 건수)를 찾는다. 없으면 -1(완결성 검사 생략).
    private int roneTotalCount(JsonNode node) {
        if (node == null) {
            return -1;
        }
        if (node.isObject()) {
            JsonNode tc = node.get("list_total_count");
            if (tc != null) {
                String digits = tc.asText().replaceAll("\\D", "");
                if (!digits.isEmpty()) {
                    return Integer.parseInt(digits);
                }
            }
        }
        if (node.isArray() || node.isObject()) {
            for (JsonNode child : node) {
                int r = roneTotalCount(child);
                if (r >= 0) {
                    return r;
                }
            }
        }
        return -1;
    }

    // STATBL_NM으로 지표/유형 판별
    private Target classify(String id, String nm) {
        if (id == null || nm == null) {
            return null;
        }
        String type = null;
        for (Map.Entry<String, String> e : TYPES.entrySet()) {
            if (nm.endsWith("_" + e.getKey())) {
                type = e.getValue();
                break;
            }
        }
        if (type == null) {
            return null;
        }
        boolean hasPeriod = PERIODS.stream().anyMatch(nm::contains);
        if (nm.contains("지역별 임대료(") && hasPeriod) {
            return new Target(id, "RENT", "임대료", type);
        }
        if (nm.contains("지역별 공실률(") && hasPeriod) {
            return new Target(id, "VACANCY", "공실률", type);
        }
        if (nm.startsWith("임대동향 수익률(분기)(") && hasPeriod) {
            return new Target(id, "YIELD", "투자수익률", type);
        }
        if (nm.contains("지역별 임대가격지수(시계열)")) {
            return new Target(id, "PRICE_INDEX", "임대가격지수", type);
        }
        return null;
    }

    // ── R-ONE API ─────────────────────────────────────────
    private JsonNode api(String path, String query) {
        String url = BASE + "/" + path + "?KEY=" + enc(rebKey) + "&Type=json&" + query;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            // 재시도 포함 시도마다 집계한다. 집계 실패가 적재를 막으면 안 되므로 따로 삼킨다.
            try {
                apiUsageService.record(ExternalApi.REB_RONE);
            } catch (RuntimeException e) {
                log.warn("R-ONE 사용량 집계 실패(적재는 계속 진행): {}", e.getMessage());
            }
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Mozilla/5.0");
                String body = rest.exchange(URI.create(url), HttpMethod.GET,
                        new HttpEntity<>(headers), String.class).getBody();
                return mapper.readTree(body);
            } catch (RuntimeException e) {
                last = e;
                sleep(2000);
            }
        }
        throw (last != null) ? last : new IllegalStateException("응답 없음: " + url);
    }

    // R-ONE 응답: { "<key>": [ {..header..}, {"row":[...]} ] } 에서 row 배열을 꺼낸다
    private List<JsonNode> extractRows(JsonNode root) {
        List<JsonNode> out = new ArrayList<>();
        if (root == null || !root.isObject()) {
            return out;
        }
        for (JsonNode block : root) {
            if (block.isArray()) {
                for (JsonNode el : block) {
                    JsonNode row = el.path("row");
                    if (row.isArray()) {
                        row.forEach(out::add);
                        return out;
                    }
                }
            }
        }
        return out;
    }

    // ── 유틸 ──────────────────────────────────────────────
    private String qcode(String w) {
        if (w == null || w.length() < 6) {
            return null;
        }
        try {
            return w.substring(0, 4) + Integer.parseInt(w.substring(4, 6));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private Double numOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String firstNonNull(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Target(String statblId, String metricCd, String metricNm, String type) {
    }
}
