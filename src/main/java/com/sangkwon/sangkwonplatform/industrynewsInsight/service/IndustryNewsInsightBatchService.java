package com.sangkwon.sangkwonplatform.industrynewsInsight.service;

import com.sangkwon.sangkwonplatform.industrynewsInsight.entity.IndustryNewsInsight;
import com.sangkwon.sangkwonplatform.industrynewsInsight.repository.IndustryNewsInsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IndustryNewsInsightBatchService {

    private final IndustryNewsInsightRepository repository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${naver.client-id}")
    private String naverClientId;

    @Value("${naver.client-secret}")
    private String naverClientSecret;

    @Value("${gemini.api-news-key}")
    private String geminiApiKey;

    private static final int PAGE_SIZE = 30;
    private static final int MAX_START = 300;
    private static final int MIN_TITLES_FOR_INSIGHT = 3;
    private static final int MAX_TITLES_FOR_GEMINI = 30;

    private static final Map<String, String> NEWS_KEYWORD_OVERRIDE = Map.of(
            "CS100007", "치킨",
            "CS200011", "변리사"
    );

    private static final List<String> BUSINESS_SIGNAL_KEYWORDS = List.of(
            "매출", "원가", "가격", "인상", "인하", "물가",
            "창업", "폐업", "가맹점", "프랜차이즈",
            "수익", "적자", "흑자", "소비", "수요",
            "트렌드", "신제품", "간편식", "배달", "포장",
            "인건비", "임대료", "정책", "지원", "규제"
    );

    private static final List<String> EXCLUDE_KEYWORDS = List.of(
            "화재", "불이 나", "구조", "진화", "소방",
            "살인", "폭행", "사망", "사고", "검거", "재판",
            "연예인", "방송", "맛집", "후기"
    );

    private static final List<String> SEARCH_SUFFIXES = List.of(
            "매출",
            "원가",
            "창업",
            "폐업",
            "가격 인상",
            "소비 트렌드",
            "프랜차이즈",
            "정책"
    );

    public void generateMonthlyInsights(Map<String, String> indutyNmMap) {

        String yearMonth = YearMonth.now().toString();

        int generated = 0;
        int skipped = 0;

        for (Map.Entry<String, String> entry : indutyNmMap.entrySet()) {
            String indutyCd = entry.getKey();
            String indutyNm = entry.getValue();

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
            }

            saveInsight(indutyCd, indutyNm, yearMonth, insightText, titles.size());
        }

        System.out.printf("완료: 인사이트 생성 %d건 / 근거부족 처리 %d건%n", generated, skipped);
    }

    private List<String> fetchAndFilterTitles(String indutyCd, String indutyNm) {

        String baseKeyword = NEWS_KEYWORD_OVERRIDE.getOrDefault(indutyCd, indutyNm);

        Set<String> titleSet = new LinkedHashSet<>();

        for (String suffix : SEARCH_SUFFIXES) {
            String query = baseKeyword + " " + suffix;
            fetchTitlesByQuery(query, baseKeyword, titleSet);
        }

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

            ResponseEntity<String> response;

            try {
                response = restTemplate.exchange(
                        URI.create(url),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );
            } catch (Exception e) {
                break;
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

    private String stripTags(String text) {
        if (text == null) return "";

        return text.replace("<b>", "")
                .replace("</b>", "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
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

                아래는 최근 '%s' 업종과 관련된 뉴스 제목 목록입니다.
                단순 뉴스 요약이 아니라, 소상공인이 업종 상황을 판단하는 데 도움이 되는 인사이트를 작성하세요.

                반드시 다음 관점 중 뉴스 제목에서 확인되는 내용만 반영하세요.
                - 수요 증가 또는 감소
                - 원가 부담
                - 가격 인상 또는 인하
                - 창업/폐업 흐름
                - 소비 트렌드 변화
                - 경쟁 심화
                - 정책/규제 영향

                출력 형식:
                [업황 요약] 1문장
                [기회 요인] 1문장
                [주의 요인] 1문장

                작성 원칙:
                - 뉴스 제목에 근거 없는 내용을 추측하지 마세요.
                - 특정 사건사고, 화재, 범죄, 정치 이슈는 제외하세요.
                - 제목 목록 자체를 나열하거나 인용하지 마세요.
                - 근거가 부족하면 "이번 달은 뚜렷한 업황 변화가 관측되지 않았습니다."라고만 답하세요.
                - 전체 250자 이내로 작성하세요.

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

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
                + "?key=" + geminiApiKey;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());

            return root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText()
                    .trim();

        } catch (Exception e) {
            System.out.println("Gemini 요약 실패: " + e.getMessage());
            return null;
        }
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