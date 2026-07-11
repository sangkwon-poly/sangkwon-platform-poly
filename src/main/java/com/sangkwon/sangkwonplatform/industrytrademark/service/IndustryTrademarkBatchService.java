package com.sangkwon.sangkwonplatform.industrytrademark.service;

import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.service.ApiUsageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// KIPRIS 상표 고급검색 -> INDUSTRY_TRADEMARK 적재.
// INDUTY 마스터의 업종명을 지정상품 검색어로 정제해 업종별 최신 출원 상표 5건씩 모은다.
// DELETE 후 재적재하는 전체 스냅샷이라 트랜잭션으로 묶으면 반복 실행해도 최종 상태가 같다(멱등).
// 주의: KIPRIS Plus 키는 사용기간이 있어 만료되면 resultCode 31(DEADLINE_HAS_EXPIRED)이 온다.
@Service
public class IndustryTrademarkBatchService {

    private static final int TOP_PER_INDUTY = 5;
    // 상표명/출원번호가 빈 행을 감안해 여유 있게 받는다
    private static final int FETCH_ROWS = 10;
    private static final long CALL_INTERVAL_MS = 300;
    private static final String BASE =
            "https://plus.kipris.or.kr/kipo-api/kipi/trademarkInfoSearchService/getAdvancedSearch";

    // 업종명 정제만으로는 지정상품 검색어가 어색한 업종의 예외 (뉴스 배치의 키워드 정제와 같은 취지)
    // 일반의원/한의원은 접미사 정제가 '일반'/'한' 같은 범용어를 만들어 따로 잡는다.
    private static final Map<String, String> KEYWORD_OVERRIDE = Map.of(
            "CS100007", "치킨",
            "CS100008", "분식",
            "CS100009", "주점",
            "CS100010", "커피",
            "CS200006", "병원",
            "CS200008", "한의원",
            "CS300043", "전자상거래");

    private final RestTemplate rest = timeoutRestTemplate();
    private final JdbcTemplate jt;
    private final ApiUsageService apiUsageService;

    // 외부 호출이 무한 대기하지 않도록 연결/읽기 타임아웃을 건다(소켓 hang 시 배치 스레드가 묶이는 것 방지)
    private static RestTemplate timeoutRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return new RestTemplate(factory);
    }

    @Value("${kipris.service-key:}")
    private String kiprisKey;

    public IndustryTrademarkBatchService(JdbcTemplate jt, ApiUsageService apiUsageService) {
        this.jt = jt;
        this.apiUsageService = apiUsageService;
    }

    @Transactional
    public long load() {
        if (kiprisKey == null || kiprisKey.isBlank()) {
            throw new IllegalStateException("KIPRIS API 키가 설정되지 않았습니다");
        }
        List<Map<String, Object>> induties = jt.queryForList(
                "SELECT INDUTY_CD, INDUTY_CD_NM FROM INDUTY ORDER BY INDUTY_CD");

        List<Object[]> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> induty : induties) {
            String indutyCd = String.valueOf(induty.get("INDUTY_CD"));
            String keyword = searchKeyword(indutyCd, String.valueOf(induty.get("INDUTY_CD_NM")));
            // 정제 결과가 한 글자면(한의원 -> 한 등) 검색 노이즈만 커져 건너뛴다
            if (keyword.length() < 2) {
                continue;
            }
            int taken = 0;
            for (Element item : fetchItems(keyword)) {
                String title = childText(item, "title");
                String applNo = childText(item, "applicationNumber");
                LocalDate applDate = toDate(childText(item, "applicationDate"));
                // 출원일이 없으면 최신순 정렬이 성립하지 않으므로 건너뛴다(가맹점수 없는 브랜드를 거르는 것과 같은 취지)
                if (title == null || applNo == null || applDate == null || !seen.add(indutyCd + "|" + applNo)) {
                    continue;
                }
                rows.add(new Object[]{indutyCd, clip(applNo, 30), clip(title, 300),
                        clip(childText(item, "applicantName"), 300), applDate,
                        clip(childText(item, "applicationStatus"), 30)});
                if (++taken >= TOP_PER_INDUTY) {
                    break;
                }
            }
            sleep(CALL_INTERVAL_MS);
        }

        // 결과가 통째로 비면(원천 장애 등) 기존 적재분을 지우지 않는다
        if (rows.isEmpty()) {
            return 0;
        }
        jt.update("DELETE FROM INDUSTRY_TRADEMARK");
        jt.batchUpdate("INSERT INTO INDUSTRY_TRADEMARK (INDUTY_CD,APPL_NO,TITLE,APPLICANT_NM,APPL_DATE,STATUS) "
                + "VALUES (?,?,?,?,?,?)", rows);
        return rows.size();
    }

    // 서울 업종명 -> 지정상품 검색어. 뉴스 배치와 같은 규칙으로 접미사를 걷어낸다.
    private String searchKeyword(String indutyCd, String indutyNm) {
        String override = KEYWORD_OVERRIDE.get(indutyCd);
        if (override != null) {
            return override;
        }
        String nm = indutyNm;
        if (nm.endsWith("업")) {
            nm = nm.substring(0, nm.length() - 1);
        }
        return nm
                .replace("전문점", "")
                .replace("사무소", "")
                .replace("의원", "")
                .replace("판매점", "")
                .replace("판매", "")
                .replace("및", " ")
                .replace("-", " ")
                .trim();
    }

    // 최신 출원순으로 상표를 검색한다. 상태 플래그는 전부 켜 출원/등록/거절 등을 모두 받는다.
    private List<Element> fetchItems(String keyword) {
        String url = BASE + "?ServiceKey=" + enc(kiprisKey)
                + "&asignProduct=" + enc(keyword)
                + "&application=true&registration=true&refused=true&expiration=true"
                + "&withdrawal=true&publication=true&cancel=true&abandonment=true"
                + "&sortSpec=applicationDate&descSort=true"
                + "&pageNo=1&numOfRows=" + FETCH_ROWS;
        Document doc = parseXml(getBytes(url));
        // 파싱 불가(점검 페이지 등)나 헤더 없는 응답은 원천 장애라 업종마다 반복된다.
        // 계속 돌면 앞쪽 업종만 남은 부분 스냅샷으로 기존 데이터를 지울 수 있어 즉시 중단시킨다.
        if (doc == null) {
            throw new IllegalStateException("KIPRIS 응답을 XML로 해석할 수 없습니다");
        }
        String success = firstTagText(doc, "successYN");
        if (success == null) {
            throw new IllegalStateException("KIPRIS 응답에 상태 헤더가 없습니다");
        }
        // 키 만료/미등록 같은 응답 오류도 업종마다 반복되므로 배치를 즉시 중단시킨다
        if (!"Y".equals(success)) {
            throw new IllegalStateException("KIPRIS 응답 오류(키 확인 필요): " + firstTagText(doc, "resultMsg"));
        }
        List<Element> out = new ArrayList<>();
        NodeList nl = doc.getElementsByTagName("item");
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i) instanceof Element e) {
                out.add(e);
            }
        }
        return out;
    }

    // ── HTTP ──────────────────────────────────────────────
    // 문자열로 받으면 charset 헤더가 없을 때 한글이 깨질 수 있어, 바이트로 받아 XML 프롤로그 인코딩에 맡긴다
    private byte[] getBytes(String url) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            // 재시도 포함 시도마다 집계한다. 집계 실패가 적재를 막으면 안 되므로 따로 삼킨다.
            try {
                apiUsageService.record(ExternalApi.KIPRIS);
            } catch (RuntimeException e) {
                System.out.println("KIPRIS 사용량 집계 실패(적재는 계속 진행): " + e.getMessage());
            }
            try {
                return rest.getForObject(URI.create(url), byte[].class);
            } catch (RuntimeException e) {
                last = e;
                sleep(2000);
            }
        }
        throw (last != null) ? last : new IllegalStateException("응답 없음: " + url);
    }

    // ── XML ───────────────────────────────────────────────
    private Document parseXml(byte[] xml) {
        if (xml == null || xml.length == 0) {
            return null;
        }
        try {
            var factory = DocumentBuilderFactory.newInstance();
            // 외부 응답에 DOCTYPE이 올 일이 없으므로 아예 금지해 XXE(외부 엔티티 주입)를 차단한다
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            return null;
        }
    }

    private String firstTagText(Document doc, String tag) {
        NodeList nl = doc.getElementsByTagName(tag);
        if (nl.getLength() == 0) {
            return null;
        }
        String s = nl.item(0).getTextContent();
        return s == null ? null : s.trim();
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
    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
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
