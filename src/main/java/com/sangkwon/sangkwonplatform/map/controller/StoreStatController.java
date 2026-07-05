package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.map.dto.request.StoreStatSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.StoreStatResponse;
import com.sangkwon.sangkwonplatform.map.service.StoreStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/store-stats")
@RequiredArgsConstructor
public class StoreStatController {

    private final StoreStatService storeStatService;

    @GetMapping
    public ApiResponse<List<StoreStatResponse>> getStoreStats(StoreStatSearchRequest request) {
        return ApiResponse.ok(storeStatService.getStoreStats(request));
    }
}
