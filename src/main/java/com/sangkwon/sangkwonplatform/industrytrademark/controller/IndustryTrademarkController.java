package com.sangkwon.sangkwonplatform.industrytrademark.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.industrytrademark.dto.response.IndustryTrademarkResponse;
import com.sangkwon.sangkwonplatform.industrytrademark.service.IndustryTrademarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/industry-trademarks")
@RequiredArgsConstructor
public class IndustryTrademarkController {

    private final IndustryTrademarkService industryTrademarkService;

    // 업종별 최신 상표 출원 (업종·상권 동향 화면의 특허·상표 카드)
    @GetMapping
    public ApiResponse<List<IndustryTrademarkResponse>> getRecentTrademarks(
            @AuthenticationPrincipal Long memberId,
            @RequestParam String indutyCd
    ) {
        return ApiResponse.ok(industryTrademarkService.getRecentTrademarks(memberId, indutyCd));
    }
}
