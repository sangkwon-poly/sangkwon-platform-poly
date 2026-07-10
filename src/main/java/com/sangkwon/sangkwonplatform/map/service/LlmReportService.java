package com.sangkwon.sangkwonplatform.map.service;

import tools.jackson.databind.JsonNode;
import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.service.ApiUsageService;
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
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class LlmReportService {

    // 무료 플랜은 회원당 월 3회까지. Pro(유효 구독)면 이 한도를 적용하지 않는다.
    private static final int FREE_MONTHLY_LIMIT = 3;

    private final TrdarRepository trdarRepository;
    private final SalesRepository salesRepository;
    private final StoreStatRepository storeStatRepository;
    private final StreetPopRepository streetPopRepository;
    private final LlmReportRepository llmReportRepository;
    private final MemberRepository memberRepository;
    private final ApiUsageService apiUsageService;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public LlmReportService(TrdarRepository trdarRepository,
                            SalesRepository salesRepository,
                            StoreStatRepository storeStatRepository,
                            StreetPopRepository streetPopRepository,
                            LlmReportRepository llmReportRepository,
                            MemberRepository memberRepository,
                            ApiUsageService apiUsageService,
                            RestClient restClient,
                            @Value("${gemini.api-key}") String apiKey,
                            @Value("${gemini.model}") String model) {
        this.trdarRepository = trdarRepository;
        this.salesRepository = salesRepository;
        this.storeStatRepository = storeStatRepository;
        this.streetPopRepository = streetPopRepository;
        this.llmReportRepository = llmReportRepository;
        this.memberRepository = memberRepository;
        this.apiUsageService = apiUsageService;
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    // 상권 지표를 모아 Gemini로 분석 리포트를 생성하고 이력을 남긴다. indutyCd가 있으면 그 업종 기준.
    // 외부 HTTP(Gemini) 호출 동안 DB 커넥션을 잡지 않도록 트랜잭션으로 묶지 않는다(이력 저장은 마지막 save 한 번).
    public LlmReportResponse generate(Long memberId, String trdarCd, String indutyCd) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API 키가 설정되지 않았습니다");
        }
        Trdar trdar = trdarRepository.findById(trdarCd)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상권을 찾을 수 없습니다"));
        String indutyNm = indutyCd == null ? null
                : llmReportRepository.findIndutyName(indutyCd)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "업종을 찾을 수 없습니다"));

        // 무료 플랜 월 한도. 프롬프트 구성이나 유료 호출 전에 막아 헛일과 슬롯 소모를 피한다.
        enforceMonthlyQuota(memberId);

        PromptData promptData = buildPrompt(trdar, indutyCd, indutyNm);

        // 유료 호출 전에 오늘 GEMINI 슬롯을 DB 카운터로 원자 선점한다(한도 초과 시 429).
        // 선점 후 호출이 실패해도 반납하지 않는다: 구글 쿼터도 시도 기준이라 집계가 실제 사용과 일치한다.
        apiUsageService.reserve(ExternalApi.GEMINI);

        JsonNode res;
        try {
            res = restClient.post()
                    .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", promptData.prompt()))))))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 호출에 실패했습니다");
        }
        // 본문 없는 200 응답이면 body(JsonNode)가 null을 돌려줄 수 있어, 역참조 전에 막는다
        if (res == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답이 비어 있습니다");
        }
        String text = res.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답이 비어 있습니다");
        }

        LlmReport saved = llmReportRepository.save(LlmReport.builder()
                .memberId(memberId)
                .trdarCd(trdarCd)
                .stdrYyquCd(promptData.quarter())
                .indutyCd(indutyCd)
                .prompt(promptData.prompt())
                .resultText(text)
                .modelName(model)
                .tokenCnt(res.path("usageMetadata").path("totalTokenCount").asLong(0))
                .build());
        return LlmReportResponse.from(saved);
    }

    // Pro면 무제한, 무료면 이번 달 3회까지. 초과하면 402로 업그레이드를 유도한다.
    private void enforceMonthlyQuota(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 정보를 찾을 수 없습니다"));
        if (member.isPro()) {
            return;
        }
        long used = llmReportRepository.countByMemberIdAndCreatedAtGreaterThanEqual(
                memberId, LocalDate.now().withDayOfMonth(1).atStartOfDay());
        if (used >= FREE_MONTHLY_LIMIT) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "이번 달 무료 AI 리포트 " + FREE_MONTHLY_LIMIT + "회를 모두 사용했어요. Pro로 업그레이드하면 무제한으로 생성할 수 있어요.");
        }
    }

    // 가장 최근 생성한 리포트. 업종 리포트와 상권 전체 리포트는 따로 관리한다
    public LlmReportResponse latest(String trdarCd, String indutyCd) {
        return llmReportRepository.findLatest(trdarCd, indutyCd, PageRequest.of(0, 1)).stream().findFirst()
                .map(LlmReportResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "생성된 리포트가 없습니다"));
    }

    // 최근 분기 지표를 모아 담백한 분석을 요구하는 프롬프트를 만든다. indutyCd가 있으면 매출·점포를 그 업종으로 좁힌다.
    // 이력에 남길 기준 분기도 같은 조회에서 뽑아 함께 돌려준다(같은 sales 조회를 두 번 하지 않도록).
    private PromptData buildPrompt(Trdar trdar, String indutyCd, String indutyNm) {
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

        String prompt = """
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
        return new PromptData(prompt, quarter);
    }

    private long nvl(Long v) {
        return v == null ? 0 : v;
    }

    private record PromptData(String prompt, String quarter) {}
}
