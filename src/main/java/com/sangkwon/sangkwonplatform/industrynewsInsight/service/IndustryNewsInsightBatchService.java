package com.sangkwon.sangkwonplatform.industrynewsInsight.service;

import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.service.ApiUsageService;
import com.sangkwon.sangkwonplatform.industrynewsInsight.entity.IndustryNewsInsight;
import com.sangkwon.sangkwonplatform.industrynewsInsight.repository.IndustryNewsInsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IndustryNewsInsightBatchService {

    private final IndustryNewsInsightRepository repository;
    private final ApiUsageService apiUsageService;
    private final RestTemplate restTemplate = timeoutRestTemplate();

    // 외부 호출이 무한 대기하지 않도록 연결/읽기 타임아웃을 건다(소켓 hang 시 배치 스레드가 묶이는 것 방지)
    private static RestTemplate timeoutRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return new RestTemplate(factory);
    }

    @Value("${naver.client-id}")
    private String naverClientId;

    @Value("${naver.client-secret}")
    private String naverClientSecret;

    @Value("${gemini.api-news-key}")
    private String geminiApiKey;

    private static final int PAGE_SIZE = 100;
    private static final int MAX_START = 1000;
    private static final int MIN_TITLES_FOR_INSIGHT = 3;
    private static final int MAX_TITLES_FOR_GEMINI = 30;

    // Gemini 호출 사이 대기시간 (밀리초) - 무료 티어 분당 요청 제한(429) 방지용
    private static final long GEMINI_CALL_INTERVAL_MS = 5000;
    // 429 발생 시 재시도 횟수 및 대기시간
    private static final int GEMINI_MAX_RETRY = 3;
    private static final long GEMINI_RETRY_WAIT_MS = 15000;

    // 네이버 API 호출 사이 대기시간 - 순간 속도 제한(rate limit) 방지용
    private static final long NAVER_CALL_INTERVAL_MS = 500;
    private static final int NAVER_MAX_RETRY = 3;
    private static final long NAVER_RETRY_WAIT_MS = 5000;

    private static final Map<String, String> ALL_INDUTY_NM = Map.ofEntries(
            Map.entry("CS100001", "한식음식점"),
            Map.entry("CS100002", "중식음식점"),
            Map.entry("CS100003", "일식음식점"),
            Map.entry("CS100004", "양식음식점"),
            Map.entry("CS100005", "제과점"),
            Map.entry("CS100006", "패스트푸드점"),
            Map.entry("CS100007", "치킨전문점"),
            Map.entry("CS100008", "분식전문점"),
            Map.entry("CS100009", "호프-간이주점"),
            Map.entry("CS100010", "커피-음료"),

            Map.entry("CS200001", "일반교습학원"),
            Map.entry("CS200002", "외국어학원"),
            Map.entry("CS200003", "예술학원"),
            Map.entry("CS200004", "컴퓨터학원"),
            Map.entry("CS200005", "스포츠 강습"),
            Map.entry("CS200006", "일반의원"),
            Map.entry("CS200007", "치과의원"),
            Map.entry("CS200008", "한의원"),
            Map.entry("CS200009", "동물병원"),
            Map.entry("CS200010", "변호사사무소"),
            Map.entry("CS200011", "변리사사무소"),
            Map.entry("CS200012", "법무사사무소"),
            Map.entry("CS200013", "기타법무서비스"),
            Map.entry("CS200014", "회계사사무소"),
            Map.entry("CS200015", "세무사사무소"),
            Map.entry("CS200016", "당구장"),
            Map.entry("CS200017", "골프연습장"),
            Map.entry("CS200018", "볼링장"),
            Map.entry("CS200019", "PC방"),
            Map.entry("CS200020", "전자게임장"),
            Map.entry("CS200021", "기타오락장"),
            Map.entry("CS200022", "복권방"),
            Map.entry("CS200023", "통신기기수리"),
            Map.entry("CS200024", "스포츠클럽"),
            Map.entry("CS200025", "자동차수리"),
            Map.entry("CS200026", "자동차미용"),
            Map.entry("CS200027", "모터사이클수리"),
            Map.entry("CS200028", "미용실"),
            Map.entry("CS200029", "네일숍"),
            Map.entry("CS200030", "피부관리실"),
            Map.entry("CS200031", "세탁소"),
            Map.entry("CS200032", "가전제품수리"),
            Map.entry("CS200033", "부동산중개업"),
            Map.entry("CS200034", "여관"),
            Map.entry("CS200035", "게스트하우스"),
            Map.entry("CS200036", "고시원"),
            Map.entry("CS200037", "노래방"),
            Map.entry("CS200038", "독서실"),
            Map.entry("CS200039", "DVD방"),
            Map.entry("CS200040", "녹음실"),
            Map.entry("CS200041", "사진관"),
            Map.entry("CS200042", "통번역서비스"),
            Map.entry("CS200043", "건축물청소"),
            Map.entry("CS200044", "여행사"),
            Map.entry("CS200045", "비디오/서적임대"),
            Map.entry("CS200046", "의류임대"),
            Map.entry("CS200047", "가정용품임대"),

            Map.entry("CS300001", "슈퍼마켓"),
            Map.entry("CS300002", "편의점"),
            Map.entry("CS300003", "컴퓨터및주변장치판매"),
            Map.entry("CS300004", "핸드폰"),
            Map.entry("CS300005", "주류도매"),
            Map.entry("CS300006", "미곡판매"),
            Map.entry("CS300007", "육류판매"),
            Map.entry("CS300008", "수산물판매"),
            Map.entry("CS300009", "청과상"),
            Map.entry("CS300010", "반찬가게"),
            Map.entry("CS300011", "일반의류"),
            Map.entry("CS300012", "한복점"),
            Map.entry("CS300013", "유아의류"),
            Map.entry("CS300014", "신발"),
            Map.entry("CS300015", "가방"),
            Map.entry("CS300016", "안경"),
            Map.entry("CS300017", "시계및귀금속"),
            Map.entry("CS300018", "의약품"),
            Map.entry("CS300019", "의료기기"),
            Map.entry("CS300020", "서적"),
            Map.entry("CS300021", "문구"),
            Map.entry("CS300022", "화장품"),
            Map.entry("CS300023", "미용재료"),
            Map.entry("CS300024", "운동/경기용품"),
            Map.entry("CS300025", "자전거 및 기타운송장비"),
            Map.entry("CS300026", "완구"),
            Map.entry("CS300027", "섬유제품"),
            Map.entry("CS300028", "화초"),
            Map.entry("CS300029", "애완동물"),
            Map.entry("CS300030", "중고가구"),
            Map.entry("CS300031", "가구"),
            Map.entry("CS300032", "가전제품"),
            Map.entry("CS300033", "철물점"),
            Map.entry("CS300034", "악기"),
            Map.entry("CS300035", "인테리어"),
            Map.entry("CS300036", "조명용품"),
            Map.entry("CS300037", "중고차판매"),
            Map.entry("CS300038", "자동차부품"),
            Map.entry("CS300039", "모터사이클및부품"),
            Map.entry("CS300040", "재생용품 판매점"),
            Map.entry("CS300041", "예술품"),
            Map.entry("CS300042", "주유소"),
            Map.entry("CS300043", "전자상거래업")
    );

    private static final Map<String, String> NEWS_KEYWORD_OVERRIDE = Map.ofEntries(
            Map.entry("CS100007", "치킨"),
            Map.entry("CS100008", "분식"),
            Map.entry("CS100009", "술집"),
            Map.entry("CS100010", "카페"),
            Map.entry("CS300043", "온라인쇼핑")
    );

    private String getSearchKeyword(String indutyCd, String indutyNm) {

        String override = NEWS_KEYWORD_OVERRIDE.get(indutyCd);

        if (override != null) {
            return override;
        }

        if (indutyNm.endsWith("업")) {
            indutyNm = indutyNm.substring(0, indutyNm.length() - 1);
        }

        return indutyNm
                .replace("전문점", "")
                .replace("사무소", "")
                .replace("의원", "")
                .replace("판매점", "")
                .replace("판매", "")
                .replace("및", " ")
                .replace("-", " ")
                .trim();
    }

    private static final List<String> SEARCH_SUFFIXES = List.of(
            "동향",
            "업황",
            "시장",
            "트렌드",
            "매출",
            "신상품",
            "간편식",
            "도시락",
            "창업",
            "폐업",
            "원가",
            "가격",
            "프랜차이즈",
            "정책",
            "소비",
            "전망"
    );

    private static final List<String> BUSINESS_SIGNAL_KEYWORDS = List.of(
            "매출",
            "원가",
            "가격",
            "인상",
            "인하",
            "물가",
            "고물가",
            "창업",
            "폐업",
            "프랜차이즈",
            "가맹",
            "소비",
            "수요",
            "시장",
            "트렌드",
            "정책",
            "지원",
            "규제",
            "인건비",
            "임대료",
            "배달",
            "포장",
            "신제품",
            "신상품",
            "간편식",
            "도시락",
            "PB",
            "협업",
            "콜라보",
            "프로모션",
            "할인",
            "주류",
            "위스키",
            "보양식"
    );

    private static final List<String> EXCLUDE_KEYWORDS = List.of(
            "화재", "불이 나", "구조", "진화", "소방",
            "살인", "폭행", "사망", "사고", "검거", "재판",
            "연예인", "방송"
    );

    // 처리(저장)한 업종 수를 돌려준다. 관리자 트리거 시 BATCH_JOB_LOG의 처리 건수로 기록된다.
    public long generateAllIndustryInsights() {
        return generateMonthlyInsights(ALL_INDUTY_NM);
    }

    public long generateMonthlyInsights(Map<String, String> indutyNmMap) {
        String yearMonth = YearMonth.now().toString();

        int generated = 0;
        int skipped = 0;

        for (Map.Entry<String, String> entry : indutyNmMap.entrySet()) {
            String indutyCd = entry.getKey();
            String indutyNm = entry.getValue();

            System.out.printf("[%s] %s 인사이트 생성 시작%n", indutyCd, indutyNm);

            List<String> titles = fetchAndFilterTitles(indutyCd, indutyNm);

            String insightText;

            if (titles.size() < MIN_TITLES_FOR_INSIGHT) {
                insightText = "이번 달은 뚜렷한 업황 변화가 관측되지 않았습니다.";
                skipped++;
            } else {
                insightText = summarizeWithGemini(indutyNm, titles);

                if (insightText == null || insightText.isBlank()) {
                    insightText = "이번 달은 뚜렷한 업황 변화가 관측되지 않았습니다.";
                    skipped++;
                } else {
                    generated++;
                }

                sleep(GEMINI_CALL_INTERVAL_MS);
            }

            saveInsight(indutyCd, indutyNm, yearMonth, insightText, titles.size());

            System.out.printf("[%s] 완료 - 기사 %d건%n", indutyCd, titles.size());
        }

        System.out.printf("전체 완료: 인사이트 생성 %d건 / 근거부족 처리 %d건%n", generated, skipped);

        return (long) generated + skipped;
    }

    private List<String> fetchAndFilterTitles(String indutyCd, String indutyNm) {
        String baseKeyword = getSearchKeyword(indutyCd, indutyNm);

        Set<String> titleSet = new LinkedHashSet<>();

        for (String suffix : SEARCH_SUFFIXES) {
            String query = baseKeyword + " " + suffix;
            fetchTitlesByQuery(query, baseKeyword, titleSet);

            if (titleSet.size() >= MAX_TITLES_FOR_GEMINI) {
                break;
            }

            sleep(NAVER_CALL_INTERVAL_MS);
        }

        System.out.println(indutyNm + " : " + titleSet.size());

        titleSet.forEach(System.out::println);

        return new ArrayList<>(titleSet);
    }

    private void fetchTitlesByQuery(String query, String baseKeyword, Set<String> titleSet) {
        int start = 1;

        while (start <= MAX_START) {
            String url = "https://openapi.naver.com/v1/search/news.json"
                    + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&display=" + PAGE_SIZE
                    + "&start=" + start
                    + "&sort=date";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", naverClientId);
            headers.set("X-Naver-Client-Secret", naverClientSecret);

            ResponseEntity<String> response = null;

            for (int attempt = 1; attempt <= NAVER_MAX_RETRY; attempt++) {
                try {
                    response = restTemplate.exchange(
                            URI.create(url),
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            String.class
                    );
                    break;
                } catch (HttpClientErrorException.TooManyRequests e) {
                    System.out.printf("네이버 429 발생: %s / %d초 대기 후 재시도 (%d/%d)%n",
                            query, NAVER_RETRY_WAIT_MS / 1000, attempt, NAVER_MAX_RETRY);
                    sleep(NAVER_RETRY_WAIT_MS);
                } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                    // 인증 실패는 '뉴스 없음'이 아니라 키 설정 오류다. 폴백을 전 업종에 저장하지 않도록 배치를 중단시킨다
                    throw new IllegalStateException("네이버 뉴스 API 인증 실패(키 확인 필요): " + e.getStatusCode(), e);
                } catch (Exception e) {
                    System.out.println("네이버 뉴스 호출 실패: " + query + " / " + e.getMessage());
                    return;
                }
            }

            if (response == null) {
                System.out.println("네이버 재시도 한도 초과, 이번 검색어는 건너뜀: " + query);
                return;
            }

            List<JsonNode> items = extractItems(response.getBody());

            if (items.isEmpty()) {
                break;
            }

            for (JsonNode item : items) {
                String title = stripTags(item.path("title").asText());
                String description = stripTags(item.path("description").asText());
                String text = title + " " + description;

                if (!text.contains(baseKeyword)) continue;
                if (EXCLUDE_KEYWORDS.stream().anyMatch(text::contains)) continue;
                if (BUSINESS_SIGNAL_KEYWORDS.stream().noneMatch(text::contains)) continue;

                titleSet.add(title);

                if (titleSet.size() >= MAX_TITLES_FOR_GEMINI) {
                    return;
                }
            }

            start += PAGE_SIZE;
            sleep(NAVER_CALL_INTERVAL_MS);
        }
    }

    private List<JsonNode> extractItems(String responseBody) {
        List<JsonNode> result = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);
            JsonNode items = root.get("items");

            if (items != null) {
                items.forEach(result::add);
            }
        } catch (Exception e) {
            System.out.println("뉴스 응답 파싱 실패: " + e.getMessage());
        }

        return result;
    }

    private String summarizeWithGemini(String indutyNm, List<String> titles) {
        String titlesBlock = String.join("\n",
                titles.stream()
                        .limit(MAX_TITLES_FOR_GEMINI)
                        .map(t -> "- " + t)
                        .toList()
        );

        String prompt = """
        당신은 예비 창업자와 소상공인을 위한 업종 동향 분석가입니다.

        아래는 '%s' 업종과 관련된 뉴스 제목 목록입니다.
        아래 뉴스 제목을 바탕으로 업종의 최근 상황을 분석하세요.

        반드시 아래 형식으로 작성하세요.

        [업황 요약]
        최근 업종 상황을 한 문장으로 설명

        [기회 요인]
        사업자가 참고할 만한 긍정적인 요인을 한 문장

        [주의 요인]
        원가, 경쟁, 정책 등 주의해야 할 사항을 한 문장

        규칙
        - 뉴스 제목에 없는 내용은 추측하지 말 것
        - 사건사고, 연예, 정치 이슈는 제외
        - 제목을 그대로 복사하지 말 것
        - 전체 250자 이하
        - 뉴스 제목이 5개 미만으로 정말 근거가 부족할 때만 "이번 달은 뚜렷한 업황 변화가 관측되지 않았습니다."라고 답할 것

        [뉴스 제목 목록]
        %s
        """.formatted(indutyNm, titlesBlock);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 300
                )
        );

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"
                + "?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        for (int attempt = 1; attempt <= GEMINI_MAX_RETRY; attempt++) {
            // 재시도 포함 시도마다 집계한다(구글 쿼터도 시도 기준). 집계 실패가 요약 생성을 막으면 안 되므로 따로 삼킨다.
            try {
                apiUsageService.record(ExternalApi.GEMINI_NEWS);
            } catch (RuntimeException e) {
                System.out.println("Gemini 사용량 집계 실패(요약은 계속 진행): " + e.getMessage());
            }
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(
                        url, requestEntity, String.class);

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());

                // get(0) 대신 path()로 탐색해 candidates/parts가 비어도(안전차단 등) NPE 없이 빈 값으로 떨어지게 한다
                String text = root.path("candidates").path(0)
                        .path("content").path("parts").path(0)
                        .path("text").asText("")
                        .trim();
                return text.isBlank() ? null : text;

            } catch (HttpClientErrorException.TooManyRequests e) {
                System.out.printf("Gemini 429(Too Many Requests) 발생, %d초 대기 후 재시도 (%d/%d)%n",
                        GEMINI_RETRY_WAIT_MS / 1000, attempt, GEMINI_MAX_RETRY);
                sleep(GEMINI_RETRY_WAIT_MS);

            } catch (Exception e) {
                System.out.println("Gemini 요약 실패: " + e.getMessage());
                return null;
            }
        }

        System.out.println("Gemini 재시도 한도 초과, 이번 업종은 건너뜀");
        return null;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String stripTags(String text) {
        if (text == null) return "";

        return text.replace("<b>", "")
                .replace("</b>", "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private void saveInsight(String indutyCd, String indutyNm, String yearMonth,
                             String insightText, int basedOnCount) {

        IndustryNewsInsight.PK pk = new IndustryNewsInsight.PK(indutyCd, yearMonth);

        IndustryNewsInsight entity = repository.findById(pk)
                .orElse(new IndustryNewsInsight());

        entity.setIndutyCd(indutyCd);
        entity.setYearMonth(yearMonth);
        entity.setIndutyNm(indutyNm);
        entity.setInsightText(insightText);
        entity.setBasedOnCount(basedOnCount);
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);
    }
}