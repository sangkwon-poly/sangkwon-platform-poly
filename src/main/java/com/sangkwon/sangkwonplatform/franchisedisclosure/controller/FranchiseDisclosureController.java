package com.sangkwon.sangkwonplatform.franchisedisclosure.controller;

import com.sangkwon.sangkwonplatform.franchisedisclosure.dto.request.FranchiseDisclosureSearchRequest;
import com.sangkwon.sangkwonplatform.franchisedisclosure.dto.response.FranchiseDisclosureResponse;
import com.sangkwon.sangkwonplatform.franchisedisclosure.service.FranchiseDisclosureService;
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
