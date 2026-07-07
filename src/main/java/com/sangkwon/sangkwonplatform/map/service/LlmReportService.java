package com.sangkwon.sangkwonplatform.map.service;

import tools.jackson.databind.JsonNode;
import com.sangkwon.sangkwonplatform.map.dto.response.LlmReportResponse;
import com.sangkwon.sangkwonplatform.map.entity.LlmReport;
import com.sangkwon.sangkwonplatform.map.entity.Sales;
import com.sangkwon.sangkwonplatform.map.entity.StoreStat;
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

    // 상권 지표를 모아 Gemini로 분석 리포트를 생성하고 이력을 남긴다
    @Transactional
    public LlmReportResponse generate(String trdarCd) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API 키가 설정되지 않았습니다");
        }
        Trdar trdar = trdarRepository.findById(trdarCd)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상권을 찾을 수 없습니다"));

        String prompt = buildPrompt(trdar);
        String quarter = latestQuarter(trdarCd);

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
                .prompt(prompt)
                .resultText(text)
                .modelName(model)
                .tokenCnt(res.path("usageMetadata").path("totalTokenCount").asLong(0))
                .build());
        return LlmReportResponse.from(saved);
    }

    // 가장 최근 생성한 리포트
    public LlmReportResponse latest(String trdarCd) {
        return llmReportRepository.findFirstByTrdarCdOrderByCreatedAtDesc(trdarCd)
                .map(LlmReportResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "생성된 리포트가 없습니다"));
    }

    private String latestQuarter(String trdarCd) {
        return salesRepository.search(null, trdarCd, null).stream()
                .map(Sales::getStdrYyquCd)
                .max(String::compareTo)
                .orElse(null);
    }

    // 최근 분기 지표를 모아 담백한 분석을 요구하는 프롬프트를 만든다
    private String buildPrompt(Trdar trdar) {
        String trdarCd = trdar.getTrdarCd();
        List<Sales> sales = salesRepository.search(null, trdarCd, null);
        TreeMap<String, Long> byQuarter = new TreeMap<>();
        sales.forEach(s -> byQuarter.merge(s.getStdrYyquCd(), nvl(s.getThsmonSelngAmt()), Long::sum));

        String quarter = byQuarter.isEmpty() ? null : byQuarter.lastKey();
        long cur = quarter == null ? 0 : byQuarter.get(quarter);
        Long prev = quarter == null ? null : byQuarter.lowerEntry(quarter) == null ? null : byQuarter.lowerEntry(quarter).getValue();
        String delta = (prev == null || prev == 0) ? "비교 불가"
                : String.format("%+.1f%%", (cur - prev) * 100.0 / prev);

        long storCo = 0;
        long opbiz = 0;
        long clsbiz = 0;
        if (quarter != null) {
            List<StoreStat> stores = storeStatRepository.search(quarter, trdarCd, null);
            storCo = stores.stream().mapToLong(s -> nvl(s.getStorCo())).sum();
            opbiz = stores.stream().mapToLong(s -> nvl(s.getOpbizStorCo())).sum();
            clsbiz = stores.stream().mapToLong(s -> nvl(s.getClsbizStorCo())).sum();
        }
        long flpop = quarter == null ? 0 : streetPopRepository.search(quarter, trdarCd).stream()
                .mapToLong(p -> nvl(p.getTotFlpopCo())).sum();

        StringBuilder top = new StringBuilder();
        if (quarter != null) {
            llmReportRepository.findTopIndusties(trdarCd, quarter).forEach(t ->
                    top.append(t.getName()).append(" ").append(Math.round(t.getAmt() / 1e8)).append("억, "));
        }

        return """
                너는 서울 상권 데이터를 다루는 애널리스트다. 아래 수치만 근거로 상권 분석 리포트를 작성하라.
                조건: 제목 없이 문단 3~4개. 과장이나 감탄 없이 담백하게, 문장마다 수치 근거를 붙인다.
                마지막 문단은 이 상권에서 창업을 검토하는 소상공인이 유의할 점 한 가지로 끝낸다.

                상권: %s (%s, %s)
                기준 분기: %s
                분기 추정 매출: %d억 원 (전분기 대비 %s)
                분기 유동인구: %d만 명
                점포 수: %d개 (개업 %d, 폐업 %d)
                매출 상위 업종: %s
                """.formatted(
                trdar.getTrdarCdNm(), trdar.getSignguNm(), trdar.getTrdarSeCdNm(),
                quarter == null ? "-" : quarter,
                Math.round(cur / 1e8), delta,
                Math.round(flpop / 1e4),
                storCo, opbiz, clsbiz,
                top.isEmpty() ? "-" : top.toString());
    }

    private long nvl(Long v) {
        return v == null ? 0 : v;
    }
}
