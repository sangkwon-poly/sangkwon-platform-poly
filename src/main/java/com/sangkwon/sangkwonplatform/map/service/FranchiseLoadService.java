package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.service.ApiUsageService;
import com.sangkwon.sangkwonplatform.global.config.LoaderHttp;
import com.sangkwon.sangkwonplatform.global.util.ExternalApiMessageSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 공정위 프랜차이즈 3종(브랜드/가맹점수/정보공개서) 적재를 앱에서 실행한다. 파이썬 04_load_franchise.py 포팅.
// 각 테이블 DELETE 후 재적재하는 전체 스냅샷 방식이라, 트랜잭션으로 묶으면 반복 실행해도 최종 상태가 같다(멱등).
@Slf4j
@Service
public class FranchiseLoadService {

    private static final int PER = 500;
    private static final int MAX_PAGE = 200;
    private static final int DISCLOSURE_START_YEAR = 2019;
    private static final int DISCLOSURE_END_YEAR = 2023;

    // franchise.ftc.go.kr가 브라우저형 User-Agent 없는 요청을 406(Access denied)으로 거부한다.
    // 정상 발급 키로도 자바 기본 UA면 차단되는 것을 curl 대조로 확인해, 공공 API 호출에 UA를 명시한다.
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private final RestTemplate rest = LoaderHttp.timed();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jt;
    private final ApiUsageService apiUsageService;

    @Value("${datagokr.service-key:}")
    private String datagokrKey;

    @Value("${ftc.franchise.service-key:}")
    private String ftcKey;

    public FranchiseLoadService(JdbcTemplate jt, ApiUsageService apiUsageService) {
        this.jt = jt;
        this.apiUsageService = apiUsageService;
    }

    @Transactional
    public long load() {
        long brands = loadBrand();
        long counts = loadCount();
        long disclosures = loadDisclosure();
        return brands + counts + disclosures;
    }

    // 1) 브랜드 목록 (data.go.kr, 2023 기준)
    private long loadBrand() {
        List<Object[]> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String base = "https://apis.data.go.kr/1130000/FftcBrandRlsInfo2_Service/getBrandinfo";
        int total = findTotalCount(getJson(base + "?serviceKey=" + enc(datagokrKey)
                + "&pageNo=1&numOfRows=1&resultType=json&jngBizCrtraYr=2023"));
        long fetched = 0;
        for (int page = 1; (long) (page - 1) * PER < total && page <= MAX_PAGE; page++) {
            JsonNode items = findItemsArray(getJson(base + "?serviceKey=" + enc(datagokrKey)
                    + "&pageNo=" + page + "&numOfRows=" + PER + "&resultType=json&jngBizCrtraYr=2023"));
            if (items == null || items.isEmpty()) {
                break;
            }
            for (JsonNode it : items) {
                fetched++;
                String mno = text(it, "brandMnno");
                if (mno == null || !seen.add(mno)) {
                    continue;
                }
                rows.add(new Object[]{mno, clip(text(it, "brandNm"), 300), clip(text(it, "corpNm"), 300),
                        clip(text(it, "jnghdqrtrsMnno"), 30), clip(text(it, "brno"), 20),
                        clip(text(it, "indutyLclasNm"), 200), clip(text(it, "indutyMlsfcNm"), 200),
                        toDate(text(it, "jngBizStrtDate"))});
            }
        }
        // 완결성 검사: 예고된 total보다 적게 받았으면 페이지 중간 빈/오류 응답(HTTP 200)으로 끊긴 부분 스냅샷이다.
        // 예외로 load()의 @Transactional을 롤백시켜 DELETE까지 취소, 기존 적재분을 보존한다(화면 통삭제 방지).
        if (fetched < total) {
            throw new IllegalStateException(
                    "FRANCHISE_BRAND 적재 미완: 기대 " + total + "행 중 " + fetched + "행만 수신(부분 스냅샷 방지, 롤백)");
        }
        // 원천 장애(HTTP 200 빈/오류 응답 등)로 수집이 비면 기존 적재분을 지우지 않는다(화면이 통째로 비는 것 방지)
        if (rows.isEmpty()) {
            return 0;
        }
        jt.update("DELETE FROM FRANCHISE_BRAND");
        jt.batchUpdate("INSERT INTO FRANCHISE_BRAND (BRAND_MGMT_NO,BRAND_NM,CORP_NM,HQ_MGMT_NO,BIZ_REG_NO,"
                + "INDUTY_LCLAS_NM,INDUTY_MLSFC_NM,BIZ_START_DE) VALUES (?,?,?,?,?,?,?,?)", rows);
        return rows.size();
    }

    // 2) 지역별 가맹점 수 (data.go.kr, 2019~2023)
    private long loadCount() {
        List<Object[]> rows = new ArrayList<>();
        String base = "https://apis.data.go.kr/1130000/FftcindutyfrcscntstatService/getindutyfrcscntstats";
        for (int yr = 2019; yr <= 2023; yr++) {
            int total = findTotalCount(getJson(base + "?serviceKey=" + enc(datagokrKey)
                    + "&pageNo=1&numOfRows=1&resultType=json&jngBizCrtraYr=" + yr));
            long fetched = 0;
            for (int page = 1; (long) (page - 1) * PER < total && page <= MAX_PAGE; page++) {
                JsonNode items = findItemsArray(getJson(base + "?serviceKey=" + enc(datagokrKey)
                        + "&pageNo=" + page + "&numOfRows=" + PER + "&resultType=json&jngBizCrtraYr=" + yr));
                if (items == null || items.isEmpty()) {
                    break;
                }
                for (JsonNode it : items) {
                    fetched++;
                    String area = orDefault(text(it, "areaNm"), "전국");
                    String induty = firstNonNull(text(it, "indutyMlsfcNm"), text(it, "indutyLclasNm"), "기타");
                    int baseYear = parseInt(text(it, "jngBizCrtraYr"), yr);
                    rows.add(new Object[]{baseYear, clip(area, 20), clip(area, 200), clip(induty, 200),
                            numOr0(text(it, "frcsCnt")), numOrNull(text(it, "frcsRate"))});
                }
            }
            // 연도별 완결성 검사: 그 해 예고 total보다 적게 받았으면 부분 스냅샷 → load()의 @Transactional 롤백
            if (fetched < total) {
                throw new IllegalStateException(
                        "FRANCHISE_COUNT 적재 미완(" + yr + "년): 기대 " + total + "행 중 " + fetched + "행만 수신(부분 스냅샷 방지, 롤백)");
            }
        }
        // 원천 장애(HTTP 200 빈/오류 응답 등)로 수집이 비면 기존 적재분을 지우지 않는다(화면이 통째로 비는 것 방지)
        if (rows.isEmpty()) {
            return 0;
        }
        jt.update("DELETE FROM FRANCHISE_COUNT");
        jt.batchUpdate("INSERT INTO FRANCHISE_COUNT (BASE_YEAR,AREA_CD,AREA_NM,INDUTY_NM,FRC_CO,FRC_RT) "
                + "VALUES (?,?,?,?,?,?)", rows);
        return rows.size();
    }

    // 3) 정보공개서 (franchise.ftc.go.kr, XML)
    private long loadDisclosure() {
        List<Object[]> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int yr = DISCLOSURE_START_YEAR; yr <= DISCLOSURE_END_YEAR; yr++) {
            String xml = getText("https://franchise.ftc.go.kr/api/search.do?type=list&yr=" + yr
                    + "&serviceKey=" + enc(ftcKey));
            List<Element> yearItems = elements(xml, "item", yr);
            if (yearItems.isEmpty()) {
                throw new IllegalStateException(
                        "FRANCHISE_DISCLOSURE 적재 미완(" + yr + "년): item 0건(부분 스냅샷 방지, 롤백)");
            }
            for (Element item : yearItems) {
                String sn = childText(item, "jngIfrmpSn");
                if (sn == null || !seen.add(sn)) {
                    continue;
                }
                rows.add(new Object[]{clip(sn, 30), clip(childText(item, "corpNm"), 300),
                        clip(childText(item, "brandNm"), 300), clip(childText(item, "brno"), 20),
                        clip(childText(item, "viwerUrl"), 1000)});
            }
        }
        // 대상 연도를 모두 정상 파싱한 뒤에도 원천 데이터가 비면 기존 적재분을 지우지 않는다.
        if (rows.isEmpty()) {
            return 0;
        }
        jt.update("DELETE FROM FRANCHISE_DISCLOSURE");
        jt.batchUpdate("INSERT INTO FRANCHISE_DISCLOSURE (DISCLOSURE_SN,CORP_NM,BRAND_NM,BIZ_REG_NO,VIEWER_URL) "
                + "VALUES (?,?,?,?,?)", rows);
        return rows.size();
    }

    // ── HTTP ──────────────────────────────────────────────
    private JsonNode getJson(String url) {
        return mapper.readTree(getText(url));
    }

    private String getText(String url) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            // 재시도 포함 시도마다 집계한다. 집계 실패가 적재를 막으면 안 되므로 따로 삼킨다.
            try {
                apiUsageService.record(ExternalApi.FTC_FRANCHISE);
            } catch (RuntimeException e) {
                log.warn("공정위 가맹사업 API 사용량 집계 실패(적재는 계속 진행): {}", e.getMessage());
            }
            try {
                return rest.exchange(
                        RequestEntity.get(URI.create(url)).header(HttpHeaders.USER_AGENT, BROWSER_UA).build(),
                        String.class).getBody();
            } catch (RuntimeException e) {
                last = e;
                sleep(2000);
            }
        }
        throw (last != null)
                ? new IllegalStateException(ExternalApiMessageSanitizer.sanitize(last))
                : new IllegalStateException("응답 없음");
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

    // ── XML ───────────────────────────────────────────────
    private List<Element> elements(String xml, String tag, int year) {
        List<Element> out = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            throw new IllegalStateException(
                    "FRANCHISE_DISCLOSURE 적재 미완(" + year + "년): XML 응답이 비어 있음(부분 스냅샷 방지, 롤백)");
        }
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList nl = doc.getElementsByTagName(tag);
            for (int i = 0; i < nl.getLength(); i++) {
                if (nl.item(i) instanceof Element e) {
                    out.add(e);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "FRANCHISE_DISCLOSURE 적재 미완(" + year + "년): XML 파싱 실패(부분 스냅샷 방지, 롤백)", e);
        }
        return out;
    }

    private String childText(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) {
            return null;
        }
        String s = nl.item(0).getTextContent();
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
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

    private LocalDate toDate(String v) {
        if (v == null) {
            return null;
        }
        String digits = v.replaceAll("\\D", "");
        if (digits.length() != 8) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(digits.substring(0, 4)),
                    Integer.parseInt(digits.substring(4, 6)), Integer.parseInt(digits.substring(6, 8)));
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

    private double numOr0(String s) {
        Double d = numOrNull(s);
        return d == null ? 0 : d;
    }

    private Double numOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Double.parseDouble(s);
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
        return "기타";
    }

    private String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
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
