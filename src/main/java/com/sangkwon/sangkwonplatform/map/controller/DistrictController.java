package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.map.dto.request.DistrictSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictGeoResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictSummaryResponse;
import com.sangkwon.sangkwonplatform.map.service.DistrictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/districts")
@RequiredArgsConstructor
public class DistrictController {

    private final DistrictService districtService;

    @GetMapping
    public ApiResponse<List<DistrictResponse>> getDistricts(DistrictSearchRequest request) {
        return ApiResponse.ok(districtService.getDistricts(request));
    }

    // 지도 폴리곤용 상권 경계 조회
    @GetMapping("/geo")
    public ApiResponse<List<DistrictGeoResponse>> getGeometries(DistrictSearchRequest request) {
        return ApiResponse.ok(districtService.getGeometries(request));
    }

    // 상권 요약 검색 (매출·유동·점포·변화)
    @GetMapping("/summary")
    public ApiResponse<List<DistrictSummaryResponse>> getSummaries(
            @RequestParam(required = false) String signguCd,
            @RequestParam(required = false) String trdarSeCd,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(districtService.getSummaries(signguCd, trdarSeCd, keyword));
    }

    // 단일 상권 조회 (상세 화면용)
    @GetMapping("/{trdarCd}")
    public ApiResponse<DistrictResponse> getDistrict(@PathVariable String trdarCd) {
        return ApiResponse.ok(districtService.getDistrict(trdarCd));
    }
}
