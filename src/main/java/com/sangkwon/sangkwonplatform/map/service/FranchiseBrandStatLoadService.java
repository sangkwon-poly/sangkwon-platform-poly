package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.service.ApiUsageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 공정위 브랜드별 가맹점 현황(getBrandFrcsStats) -> FRANCHISE_BRAND_STAT 적재.
// 전체 브랜드를 받아 공정위 업종 중분류를 서울 업종코드로 매핑하고, 업종별 가맹점수 상위 5개만 남긴다.
// DELETE 후 재적재하는 전체 스냅샷이라 트랜잭션으로 묶으면 반복 실행해도 최종 상태가 같다(멱등).
// 주의: 이 API는 data.go.kr에서 서비스키에 별도 활용신청이 필요하다(미신청 키는 게이트웨이가 403).
@Service
public class FranchiseBrandStatLoadService {

    private static final int PER = 500;
    private static final int MAX_PAGE = 200;
    private static final int TOP_PER_INDUTY = 5;
    private static final String BASE =
            "https://apis.data.go.kr/1130000/FftcBrandFrcsStatsService/getBrandFrcsStats";

    // 공정위 업종 중분류명 -> 서울 상권 업종코드. 같은 가맹정보 계열 API(getBrandinfo 2023 전수 스캔)의
    // 실제 분류값 기준으로, 서울 분류와 1:1로 대응되는 업종만 매핑한다(피자, 기타 외식처럼 어긋나는 것은 제외).
    // 원본 값에 후행 공백이 섞인 분류가 있어(부동산 중개 등) 조회 전 trim이 필수다.
    private static final Map<String, String> FTC_TO_SEOUL = Map.ofEntries(
            Map.entry("한식", "CS100001"),
            Map.entry("중식", "CS100002"),
            Map.entry("일식", "CS100003"),
            Map.entry("서양식", "CS100004"),
            Map.entry("제과제빵", "CS100005"),
            Map.entry("패스트푸드", "CS100006"),
            Map.entry("치킨", "CS100007"),
            Map.entry("분식", "CS100008"),
            Map.entry("주점", "CS100009"),
            Map.entry("커피", "CS100010"),
            Map.entry("음료 (커피 외)", "CS100010"),
            Map.entry("교육 (교과)", "CS200001"),
            Map.entry("교육 (외국어)", "CS200002"),
            Map.entry("PC방", "CS200019"),
            Map.entry("이미용", "CS200028"),
            Map.entry("세탁", "CS200031"),
            Map.entry("부동산 중개", "CS200033"),
            Map.entry("편의점", "CS300002"),
            Map.entry("안경", "CS300016"),
            Map.entry("약국", "CS300018"),
            Map.entry("화장품", "CS300022"),
            Map.entry("반려동물 관련", "CS300029"));

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jt;
    private final ApiUsageService apiUsageService;

    @Value("${datagokr.service-key:}")
    private String datagokrKey;

    public FranchiseBrandStatLoadService(JdbcTemplate jt, ApiUsageService apiUsageService) {
        this.jt = jt;
        this.apiUsageService = apiUsageService;
    }

    @Transactional
    public long load() {
        // 공정위 데이터는 연 단위로 갱신되므로 데이터가 있는 가장 최근 기준연도를 찾아 쓴다
        int year = 0;
        int total = 0;
        for (int yr = LocalDate.now().getYear(); yr >= 2022 && total == 0; yr--) {
            total = findTotalCount(getJson(pageUrl(yr, 1, 1)));
            year = yr;
        }
        // 응답이 전부 비면 기존 적재분을 지우지 않고 그대로 둔다(원천 장애 시 화면이 통째로 비는 것 방지)
        if (total == 0) {
            return 0;
        }

        // (서울 업종코드 -> 후보 브랜드 행). 같은 브랜드가 중복 응답돼도 첫 행만 쓴다.
        Map<String, List<Object[]>> byInduty = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (int page = 1; (long) (page - 1) * PER < total && page <= MAX_PAGE; page++) {
            JsonNode items = findItemsArray(getJson(pageUrl(year, page, PER)));
            if (items == null || items.isEmpty()) {
                break;
            }
            for (JsonNode it : items) {
                String ftcInduty = trimToNull(text(it, "indutyMlsfcNm"));
                String indutyCd = ftcInduty == null ? null : FTC_TO_SEOUL.get(ftcInduty);
                String brandNm = text(it, "brandNm");
                Long frcsCnt = longOrNull(text(it, "frcsCnt"));
                // 미매핑 업종이거나 가맹점수가 없어 순위를 매길 수 없는 행은 건너뛴다
                if (indutyCd == null || brandNm == null || frcsCnt == null) {
                    continue;
                }
                if (!seen.add(indutyCd + "|" + brandNm)) {
                    continue;
                }
                byInduty.computeIfAbsent(indutyCd, k -> new ArrayList<>()).add(new Object[]{
                        indutyCd, year, clip(brandNm, 300), clip(text(it, "corpNm"), 300),
                        clip(ftcInduty, 200), frcsCnt,
                        longOrNull(text(it, "avrgSlsAmt")), longOrNull(text(it, "newFrcsRgsCnt")),
                        longOrNull(text(it, "ctrtEndCnt")), longOrNull(text(it, "ctrtCncltnCnt")),
                        longOrNull(text(it, "nmChgCnt"))});
            }
        }

        // 업종별 가맹점수 내림차순 상위 5개만 남긴다
        List<Object[]> rows = new ArrayList<>();
        for (List<Object[]> candidates : byInduty.values()) {
            candidates.sort((a, b) -> Long.compare((Long) b[5], (Long) a[5]));
            rows.addAll(candidates.subList(0, Math.min(TOP_PER_INDUTY, candidates.size())));
        }

        // 건수는 있다는데 파싱/매핑 결과가 통째로 비면(원천 구조나 분류명 변경) 기존 적재분을 지우지 않는다
        if (rows.isEmpty()) {
            return 0;
        }

        jt.update("DELETE FROM FRANCHISE_BRAND_STAT");
        jt.batchUpdate("INSERT INTO FRANCHISE_BRAND_STAT (INDUTY_CD,BASE_YEAR,BRAND_NM,CORP_NM,FTC_INDUTY_NM,"
                + "FRCS_CNT,AVG_SALES_AMT,NEW_FRCS_RGS_CNT,CTRT_END_CNT,CTRT_CNCLTN_CNT,NM_CHG_CNT) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?)", rows);
        return rows.size();
    }

    // 연도 파라미터는 yr(다른 가맹정보 API의 jngBizCrtraYr와 다름, 실호출로 확인)
    private String pageUrl(int year, int page, int rows) {
        return BASE + "?serviceKey=" + enc(datagokrKey)
                + "&pageNo=" + page + "&numOfRows=" + rows + "&resultType=json&yr=" + year;
    }

    // ── HTTP ──────────────────────────────────────────────
    private JsonNode getJson(String url) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            // 재시도 포함 시도마다 집계한다. 집계 실패가 적재를 막으면 안 되므로 따로 삼킨다.
            try {
                apiUsageService.record(ExternalApi.FTC_FRANCHISE);
            } catch (RuntimeException e) {
                System.out.println("공정위 가맹사업 API 사용량 집계 실패(적재는 계속 진행): " + e.getMessage());
            }
            try {
                return mapper.readTree(rest.getForObject(URI.create(url), String.class));
            } catch (RuntimeException e) {
                last = e;
                sleep(2000);
            }
        }
        throw (last != null) ? last : new IllegalStateException("응답 없음: " + url);
    }

    // ── data.go.kr 응답 형태 탐색(중첩 구조에서 items 배열/totalCount 찾기) ──
    private JsonNode findItemsArray(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray() && !node.isEmpty() && node.get(0).isObject()) {
            return node;
        }
        if (node.isArray() || node.isObject()) {
            for (JsonNode child : node) {
                JsonNode r = findItemsArray(child);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private int findTotalCount(JsonNode node) {
        if (node == null) {
            return 0;
        }
        if (node.isObject()) {
            JsonNode tc = node.get("totalCount");
            if (tc != null) {
                int v = parseInt(tc.asText(), 0);
                if (v > 0) {
                    return v;
                }
            }
        }
        if (node.isArray() || node.isObject()) {
            for (JsonNode child : node) {
                int r = findTotalCount(child);
                if (r > 0) {
                    return r;
                }
            }
        }
        return 0;
    }

    // ── 유틸 ──────────────────────────────────────────────
    private String enc(String key) {
        return URLEncoder.encode(key == null ? "" : key, StandardCharsets.UTF_8);
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Long longOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Math.round(Double.parseDouble(s.replace(",", "")));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private int parseInt(String s, int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (RuntimeException e) {
            return def;
        }
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
}
