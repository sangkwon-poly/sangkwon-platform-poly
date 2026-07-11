package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.service.ApiUsageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// 서울 열린데이터광장 상권 분석 팩트 7종 적재를 앱에서 실행한다. 파이썬 03_load_seoul_facts.py 포팅.
// 파이썬처럼 테이블 컬럼과 API 필드를 동적 매핑한다(하드코딩 없음). 각 테이블 DELETE 후 재적재하는 전체 스냅샷.
// 대용량(점포 160만 등)이라 트랜잭션으로 묶지 않고 배치마다 커밋한다(중간 실패 시 부분 적재, 재실행으로 복구).
// 제약(FK/CHECK) 위반 행은 배치 실패 시 행 단위로 재시도하며 건너뛴다(파이썬은 제약을 비활성화하지만 앱은 DDL을 피함).
@Service
public class SeoulFactsLoadService {

    private static final int PAGE = 1000;   // 서울 API 한 번에 최대 1000행
    private static final int BATCH = 5000;  // 인서트 배치 크기

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jt;
    private final ApiUsageService apiUsageService;

    @Value("${seoul.opendata.key:}")
    private String seoulKey;

    public SeoulFactsLoadService(JdbcTemplate jt, ApiUsageService apiUsageService) {
        this.jt = jt;
        this.apiUsageService = apiUsageService;
    }

    public long loadSales() {
        return load("VwsmTrdarSelngQq", "SALES", Map.of("SVC_INDUTY_CD", "INDUTY_CD"), true);
    }

    public long loadStoreStat() {
        return load("VwsmTrdarStorQq", "STORE_STAT", Map.of("SVC_INDUTY_CD", "INDUTY_CD"), true);
    }

    public long loadTrdarChange() {
        return load("VwsmTrdarIxQq", "TRDAR_CHANGE", Map.of(), false);
    }

    public long loadStreetPop() {
        return load("VwsmTrdarFlpopQq", "STREET_POP", Map.of(), false);
    }

    public long loadResidentPop() {
        return load("VwsmTrdarRepopQq", "RESIDENT_POP",
                Map.of("APT_HSHLD_CO", "APT_HSHLD_RESIDENT_CO", "NON_APT_HSHLD_CO", "NON_APT_HSHLD_RESIDENT_CO"), false);
    }

    public long loadAttraction() {
        return load("VwsmTrdarFcltyQq", "ATTRACTION", Map.of("BUS_STTN_CO", "BUS_STOP_CO"), false);
    }

    public long loadApt() {
        return load("InfoTrdarAptQq", "APT", Map.ofEntries(
                Map.entry("APT_HSMP_CO", "APT_COMPLX_CO"),
                Map.entry("AE_66_SQMT_HSHLD_CO", "AREA_66_HSHLD_CO"),
                Map.entry("AE_99_SQMT_HSHLD_CO", "AREA_99_HSHLD_CO"),
                Map.entry("AE_132_SQMT_HSHLD_CO", "AREA_132_HSHLD_CO"),
                Map.entry("AE_165_SQMT_HSHLD_CO", "AREA_165_HSHLD_CO"),
                Map.entry("PC_1_HDMIL_HSHLD_CO", "PRC_1_HSHLD_CO"),
                Map.entry("PC_2_HDMIL_HSHLD_CO", "PRC_2_HSHLD_CO"),
                Map.entry("PC_3_HDMIL_HSHLD_CO", "PRC_3_HSHLD_CO"),
                Map.entry("PC_4_HDMIL_HSHLD_CO", "PRC_4_HSHLD_CO"),
                Map.entry("PC_5_HDMIL_HSHLD_CO", "PRC_5_HSHLD_CO"),
                Map.entry("PC_6_HDMIL_ABOVE_HSHLD_CO", "PRC_6_HSHLD_CO"),
                Map.entry("AVRG_AE", "AVRG_AREA"),
                Map.entry("AVRG_MKTC", "AVRG_MRKT_PRC")), false);
    }

    private long load(String svc, String table, Map<String, String> rename, boolean hasInduty) {
        Map<String, String> colType = tableColumns(table);
        if (colType.isEmpty()) {
            throw new IllegalStateException("테이블 컬럼을 찾을 수 없음: " + table);
        }

        JsonNode first = fetchBlock(svc, 1, 1);
        int total = first == null ? 0 : parseInt(text(first, "list_total_count"), 0);
        JsonNode firstRows = first == null ? null : first.path("row");
        if (total == 0 || firstRows == null || !firstRows.isArray() || firstRows.isEmpty()) {
            return 0;
        }
        JsonNode sample = firstRows.get(0);

        // (apiField, tableCol) 매핑: API가 주고 테이블에도 있는 컬럼 + rename
        List<String[]> pairs = new ArrayList<>();
        for (String tc : new TreeSet<>(colType.keySet())) {
            if (!sample.path(tc).isMissingNode()) {
                pairs.add(new String[]{tc, tc});
            }
        }
        for (Map.Entry<String, String> e : rename.entrySet()) {
            if (!sample.path(e.getKey()).isMissingNode() && colType.containsKey(e.getValue())) {
                pairs.add(new String[]{e.getKey(), e.getValue()});
            }
        }

        List<String> cols = pairs.stream().map(p -> p[1]).toList();
        String sql = "INSERT INTO " + table + " (" + String.join(",", cols) + ") VALUES ("
                + String.join(",", cols.stream().map(c -> "?").toList()) + ")";

        jt.update("DELETE FROM " + table);

        Map<String, String> induty = new LinkedHashMap<>();
        long loaded = 0;
        List<Object[]> batch = new ArrayList<>();
        for (int start = 1; start <= total; start += PAGE) {
            JsonNode block = fetchBlock(svc, start, start + PAGE - 1);
            JsonNode rows = block == null ? null : block.path("row");
            if (rows == null || !rows.isArray() || rows.isEmpty()) {
                break;
            }
            for (JsonNode r : rows) {
                if (hasInduty) {
                    String code = text(r, "SVC_INDUTY_CD");
                    if (code != null) {
                        induty.putIfAbsent(code, text(r, "SVC_INDUTY_CD_NM"));
                    }
                }
                Object[] row = new Object[pairs.size()];
                for (int i = 0; i < pairs.size(); i++) {
                    String[] p = pairs.get(i);
                    row[i] = value(text(r, p[0]), colType.get(p[1]));
                }
                batch.add(row);
            }
            if (batch.size() >= BATCH) {
                loaded += insertBatch(sql, batch);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            loaded += insertBatch(sql, batch);
        }

        if (hasInduty && !induty.isEmpty()) {
            mergeInduty(induty);
        }
        return loaded;
    }

    // 배치 인서트. 제약 위반 등으로 배치가 실패하면 행 단위로 재시도하며 실패 행은 건너뛴다.
    private int insertBatch(String sql, List<Object[]> batch) {
        try {
            jt.batchUpdate(sql, batch);
            return batch.size();
        } catch (DataAccessException e) {
            int ok = 0;
            for (Object[] row : batch) {
                try {
                    jt.update(sql, row);
                    ok++;
                } catch (DataAccessException ignore) {
                    // 개별 제약 위반 행은 스킵
                }
            }
            return ok;
        }
    }

    private void mergeInduty(Map<String, String> induty) {
        List<Object[]> args = new ArrayList<>();
        for (Map.Entry<String, String> e : induty.entrySet()) {
            args.add(new Object[]{e.getKey(), e.getValue()});
        }
        jt.batchUpdate("MERGE INTO INDUTY d USING (SELECT ? cd, ? nm FROM dual) s ON (d.INDUTY_CD = s.cd) "
                + "WHEN MATCHED THEN UPDATE SET INDUTY_CD_NM = s.nm "
                + "WHEN NOT MATCHED THEN INSERT (INDUTY_CD, INDUTY_CD_NM) VALUES (s.cd, s.nm)", args);
    }

    // 테이블 컬럼명 -> 데이터 타입
    private Map<String, String> tableColumns(String table) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> r : jt.queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ?", table)) {
            out.put(String.valueOf(r.get("COLUMN_NAME")), String.valueOf(r.get("DATA_TYPE")));
        }
        return out;
    }

    private JsonNode fetchBlock(String svc, int start, int end) {
        String url = "http://openapi.seoul.go.kr:8088/" + seoulKey + "/json/" + svc + "/" + start + "/" + end + "/";
        JsonNode root = getJson(url);
        JsonNode block = root.path(svc);
        return block.isMissingNode() ? null : block;
    }

    private JsonNode getJson(String url) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            // 재시도 포함 시도마다 집계한다. 집계 실패가 적재를 막으면 안 되므로 따로 삼킨다.
            try {
                apiUsageService.record(ExternalApi.SEOUL);
            } catch (RuntimeException e) {
                System.out.println("서울 오픈API 사용량 집계 실패(적재는 계속 진행): " + e.getMessage());
            }
            try {
                return mapper.readTree(rest.getForObject(URI.create(url), String.class));
            } catch (RuntimeException e) {
                last = e;
                sleep(2000);
            }
        }
        // 예외 메시지가 배치 로그에 저장되므로 URL에 박힌 API 키를 가린다
        String masked = (seoulKey == null || seoulKey.isBlank()) ? url : url.replace(seoulKey, "***");
        throw new IllegalStateException("서울 오픈API 응답 실패: " + masked, last);
    }

    // 숫자 컬럼이면 Double(빈값 null), 아니면 문자열 그대로
    private Object value(String raw, String dataType) {
        boolean numeric = dataType != null
                && (dataType.startsWith("NUMBER") || dataType.startsWith("FLOAT") || dataType.startsWith("BINARY"));
        if (!numeric) {
            return raw;
        }
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private int parseInt(String s, int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
