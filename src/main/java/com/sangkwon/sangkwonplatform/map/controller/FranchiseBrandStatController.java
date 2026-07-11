package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.FranchiseBrandStatResponse;
import com.sangkwon.sangkwonplatform.map.service.FranchiseBrandStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/franchise-brand-stats")
@RequiredArgsConstructor
public class FranchiseBrandStatController {

    private final FranchiseBrandStatService franchiseBrandStatService;

    // 업종별 가맹점수 상위 브랜드 (업종·상권 동향 화면의 주요 프랜차이즈 카드)
    @GetMapping
    public ApiResponse<List<FranchiseBrandStatResponse>> getTopBrands(
            @AuthenticationPrincipal Long memberId,
            @RequestParam String indutyCd
    ) {
        return ApiResponse.ok(franchiseBrandStatService.getTopBrands(memberId, indutyCd));
    }
}
