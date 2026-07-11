package com.sangkwon.sangkwonplatform.industrynewsInsight.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.industrynewsInsight.dto.response.IndustryNewsInsightResponse;
import com.sangkwon.sangkwonplatform.industrynewsInsight.service.IndustryNewsInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/industry-news-insights")
@RequiredArgsConstructor
public class IndustryNewsInsightController {

    private final IndustryNewsInsightService industryNewsInsightService;

    @GetMapping("/latest")
    public ApiResponse<IndustryNewsInsightResponse> getLatestInsight(
            @AuthenticationPrincipal Long memberId,
            @RequestParam String indutyCd
    ) {
        return ApiResponse.ok(
                industryNewsInsightService.getLatestInsight(memberId, indutyCd)
        );
    }
}