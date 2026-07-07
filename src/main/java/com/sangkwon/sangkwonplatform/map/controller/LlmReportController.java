package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.LlmReportResponse;
import com.sangkwon.sangkwonplatform.map.service.LlmReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm-reports")
@RequiredArgsConstructor
public class LlmReportController {

    private final LlmReportService llmReportService;

    // 상권 AI 분석 생성 (이력 저장)
    @PostMapping("/{trdarCd}")
    public ApiResponse<LlmReportResponse> generate(@PathVariable String trdarCd) {
        return ApiResponse.ok(llmReportService.generate(trdarCd));
    }

    // 가장 최근 생성분 조회
    @GetMapping("/{trdarCd}/latest")
    public ApiResponse<LlmReportResponse> latest(@PathVariable String trdarCd) {
        return ApiResponse.ok(llmReportService.latest(trdarCd));
    }
}
