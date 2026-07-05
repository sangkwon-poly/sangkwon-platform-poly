package com.sangkwon.sangkwonplatform.franchisebrand.controller;

import com.sangkwon.sangkwonplatform.franchisebrand.dto.request.FranchiseBrandSearchRequest;
import com.sangkwon.sangkwonplatform.franchisebrand.dto.response.FranchiseBrandResponse;
import com.sangkwon.sangkwonplatform.franchisebrand.service.FranchiseBrandService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/franchise-brands")
@RequiredArgsConstructor
public class FranchiseBrandController {

    private final FranchiseBrandService franchiseBrandService;

    @GetMapping
    public ApiResponse<List<FranchiseBrandResponse>> getFranchiseBrands(FranchiseBrandSearchRequest request) {
        return ApiResponse.ok(franchiseBrandService.getFranchiseBrands(request));
    }
}
