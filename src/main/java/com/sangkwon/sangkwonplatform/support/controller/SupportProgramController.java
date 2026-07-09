package com.sangkwon.sangkwonplatform.support.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.support.dto.request.SupportProgramSearchRequest;
import com.sangkwon.sangkwonplatform.support.dto.response.SupportProgramDetailResponse;
import com.sangkwon.sangkwonplatform.support.dto.response.SupportProgramPageResponse;
import com.sangkwon.sangkwonplatform.support.service.SupportProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 지원사업 통합 조회 (기업마당 + K-Startup). 공개 조회.
@RestController
@RequestMapping("/api/support-programs")
@RequiredArgsConstructor
public class SupportProgramController {

    private final SupportProgramService supportProgramService;

    @GetMapping
    public ApiResponse<SupportProgramPageResponse> list(SupportProgramSearchRequest request,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(supportProgramService.search(request, page, size));
    }

    @GetMapping("/{sourceCd}/{programId}")
    public ApiResponse<SupportProgramDetailResponse> detail(@PathVariable String sourceCd,
                                                            @PathVariable String programId) {
        return ApiResponse.ok(supportProgramService.getDetail(sourceCd, programId));
    }
}
