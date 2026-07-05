package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.request.FranchiseDisclosureSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.FranchiseDisclosureResponse;
import com.sangkwon.sangkwonplatform.map.service.FranchiseDisclosureService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/franchise-disclosures")
@RequiredArgsConstructor
public class FranchiseDisclosureController {

    private final FranchiseDisclosureService franchiseDisclosureService;

    @GetMapping
    public ApiResponse<List<FranchiseDisclosureResponse>> getFranchiseDisclosures(FranchiseDisclosureSearchRequest request) {
        return ApiResponse.ok(franchiseDisclosureService.getFranchiseDisclosures(request));
    }
}
