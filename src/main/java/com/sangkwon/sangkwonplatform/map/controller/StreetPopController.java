package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.map.dto.request.StreetPopSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.StreetPopResponse;
import com.sangkwon.sangkwonplatform.map.service.StreetPopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/street-pops")
@RequiredArgsConstructor
public class StreetPopController {

    private final StreetPopService streetPopService;

    @GetMapping
    public ApiResponse<List<StreetPopResponse>> getStreetPops(StreetPopSearchRequest request) {
        return ApiResponse.ok(streetPopService.getStreetPops(request));
    }
}
