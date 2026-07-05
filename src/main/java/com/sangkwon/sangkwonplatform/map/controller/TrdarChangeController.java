package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.map.dto.request.TrdarChangeSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.TrdarChangeResponse;
import com.sangkwon.sangkwonplatform.map.service.TrdarChangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trdar-changes")
@RequiredArgsConstructor
public class TrdarChangeController {

    private final TrdarChangeService trdarChangeService;

    @GetMapping
    public ApiResponse<List<TrdarChangeResponse>> getTrdarChanges(TrdarChangeSearchRequest request) {
        return ApiResponse.ok(trdarChangeService.getTrdarChanges(request));
    }
}
