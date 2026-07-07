package com.sangkwon.sangkwonplatform.map.service;

import tools.jackson.databind.JsonNode;
import com.sangkwon.sangkwonplatform.map.dto.response.LlmReportResponse;
import com.sangkwon.sangkwonplatform.map.entity.LlmReport;
import com.sangkwon.sangkwonplatform.map.entity.Sales;
import com.sangkwon.sangkwonplatform.map.entity.StoreStat;
import com.sangkwon.sangkwonplatform.map.entity.StreetPop;
import com.sangkwon.sangkwonplatform.map.entity.Trdar;
import com.sangkwon.sangkwonplatform.map.repository.LlmReportRepository;
import com.sangkwon.sangkwonplatform.map.repository.SalesRepository;
import com.sangkwon.sangkwonplatform.map.repository.StoreStatRepository;
import com.sangkwon.sangkwonplatform.map.repository.StreetPopRepository;
import com.sangkwon.sangkwonplatform.map.repository.TrdarRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class LlmReportService {

    private final TrdarRepository trdarRepository;
    private final SalesRepository salesRepository;
    private final StoreStatRepository storeStatRepository;
    private final StreetPopRepository streetPopRepository;
    private final LlmReportRepository llmReportRepository;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public LlmReportService(TrdarRepository trdarRepository,
                            SalesRepository salesRepository,
                            StoreStatRepository storeStatRepository,
                            StreetPopRepository streetPopRepository,
                            LlmReportRepository llmReportRepository,
                            RestClient restClient,
                            @Value("${gemini.api-key}") String apiKey,
                            @Value("${gemini.model}") String model) {
        this.trdarRepository = trdarRepository;
        this.salesRepository = salesRepository;
        this.storeStatRepository = storeStatRepository;
        this.streetPopRepository = streetPopRepository;
        this.llmReportRepository = llmReportRepository;
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    // 상권 지표를 모아 Gemini로 분석 리포트를 생성하고 이력을 남긴다. indutyCd가 있으면 그 업종 기준
    @Transactional
    public LlmReportResponse generate(String trdarCd, String indutyCd) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API 키가 설정되지 않았습니다");
        }
        Trdar trdar = trdarRepository.findById(trdarCd)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상권을 찾을 수 없습니다"));
        String indutyNm = indutyCd == null ? null
                : llmReportRepository.findIndutyName(indutyCd)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "업종을 찾을 수 없습니다"));

        String prompt = buildPrompt(trdar, indutyCd, indutyNm);
        String quarter = latestQuarter(trdarCd, indutyCd);

        JsonNode res;
        try {
            res = restClient.post()
                    .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}",
                            model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 호출에 실패했습니다");
        }
        String text = res.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답이 비어 있습니다");
        }

        LlmReport saved = llmReportRepository.save(LlmReport.builder()
                .trdarCd(trdarCd)
                .stdrYyquCd(quarter)
                .indutyCd(indutyCd)
                .prompt(prompt)
                .resultText(text)
                .modelName(model)
                .tokenCnt(res.path("usageMetadata").path("totalTokenCount").asLong(0))
                .build());
        return LlmReportResponse.from(saved);
    }

    // 가장 최근 생성한 리포트. 업종 리포트와 상권 전체 리포트는 따로 관리한다
    public LlmReportResponse latest(String trdarCd, String indutyCd) {
        return llmReportRepository.findLatest(trdarCd, indutyCd).stream().findFirst()
                .map(LlmReportResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "생성된 리포트가 없습니다"));
    }

    private String latestQuarter(String trdarCd, String indutyCd) {
        return salesRepository.search(null, trdarCd, indutyCd).stream()
                .map(Sales::getStdrYyquCd)
                .max(String::compareTo)
                .orElse(null);
    }

    // 최근 분기 지표를 모아 담백한 분석을 요구하는 프롬프트를 만든다. indutyCd가 있으면 매출·점포를 그 업종으로 좁힌다
    private String buildPrompt(Trdar trdar, String indutyCd, String indutyNm) {
        String trdarCd = trdar.getTrdarCd();
        List<Sales> sales = salesRepository.search(null, trdarCd, indutyCd);
        TreeMap<String, Long> byQuarter = new TreeMap<>();
        sales.forEach(s -> byQuarter.merge(s.getStdrYyquCd(), nvl(s.getThsmonSelngAmt()), Long::sum));

        String quarter = byQuarter.isEmpty() ? null : byQuarter.lastKey();
        long cur = quarter == null ? 0 : byQuarter.get(quarter);
        Long prev = quarter == null ? null : byQuarter.lowerEntry(quarter) == null ? null : byQuarter.lowerEntry(quarter).getValue();
        String delta = (prev == null || prev == 0) ? "비교 불가"
                : String.format("%+.1f%%", (cur - prev) * 100.0 / prev);

        // 적재 시차로 기준 분기 행이 없으면 0이 아니라 집계 없음으로 넣는다
        List<StoreStat> stores = quarter == null ? List.of() : storeStatRepository.search(quarter, trdarCd, indutyCd);
        long storCo = stores.stream().mapToLong(s -> nvl(s.getStorCo())).sum();
        long opbiz = stores.stream().mapToLong(s -> nvl(s.getOpbizStorCo())).sum();
        long clsbiz = stores.stream().mapToLong(s -> nvl(s.getClsbizStorCo())).sum();
        List<StreetPop> pops = quarter == null ? List.of() : streetPopRepository.search(quarter, trdarCd);
        long flpop = pops.stream().mapToLong(p -> nvl(p.getTotFlpopCo())).sum();

        // 업종 리포트에선 상위 업종 나열 대신 대상 업종을 밝힌다
        StringBuilder top = new StringBuilder();
        if (indutyCd == null && quarter != null) {
            llmReportRepository.findTopIndusties(trdarCd, quarter).forEach(t ->
                    top.append(t.getName()).append(" ").append(Math.round(t.getAmt() / 1e8)).append("억, "));
        }

        return """
                너는 서울 상권 데이터를 다루는 애널리스트다. 아래 수치만 근거로 %s 분석 리포트를 작성하라.
                조건: 제목 없이 문단 3~4개. 과장이나 감탄 없이 담백하게, 문장마다 수치 근거를 붙인다.
                집계 없음으로 표시된 항목은 근거로 삼지 말고 언급하지도 않는다.
                마지막 문단은 이 상권에서 창업을 검토하는 소상공인이 유의할 점 한 가지로 끝낸다.

                상권: %s (%s, %s)
                기준 분기: %s
                분기 추정 매출: %s
                분기 유동인구: %s
                점포 수: %s
                %s
                """.formatted(
                indutyNm == null ? "상권" : indutyNm + " 업종",
                trdar.getTrdarCdNm(), trdar.getSignguNm(), trdar.getTrdarSeCdNm(),
                quarter == null ? "-" : quarter,
                quarter == null ? "집계 없음" : Math.round(cur / 1e8) + "억 원 (전분기 대비 " + delta + ")",
                pops.isEmpty() ? "집계 없음" : Math.round(flpop / 1e4) + "만 명" + (indutyCd == null ? "" : " (상권 전체 기준)"),
                stores.isEmpty() ? "집계 없음" : "%d개 (개업 %d, 폐업 %d)".formatted(storCo, opbiz, clsbiz),
                indutyNm == null
                        ? "매출 상위 업종: " + (top.isEmpty() ? "-" : top.toString())
                        : "대상 업종: " + indutyNm + " (매출·점포는 이 업종 기준)");
    }

    private long nvl(Long v) {
        return v == null ? 0 : v;
    }
}
