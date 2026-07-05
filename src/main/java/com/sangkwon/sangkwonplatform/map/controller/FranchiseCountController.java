package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.request.FranchiseCountSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.FranchiseCountResponse;
import com.sangkwon.sangkwonplatform.map.service.FranchiseCountService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/franchise-counts")
@RequiredArgsConstructor
public class FranchiseCountController {

    private final FranchiseCountService franchiseCountService;

    @GetMapping
    public ApiResponse<List<FranchiseCountResponse>> getFranchiseCounts(FranchiseCountSearchRequest request) {
        return ApiResponse.ok(franchiseCountService.getFranchiseCounts(request));
    }
}
