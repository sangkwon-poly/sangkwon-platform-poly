package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.request.AptSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.AptResponse;
import com.sangkwon.sangkwonplatform.map.service.AptService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/apts")
@RequiredArgsConstructor
public class AptController {

    private final AptService aptService;

    @GetMapping
    public ApiResponse<List<AptResponse>> getApts(AptSearchRequest request) {
        return ApiResponse.ok(aptService.getApts(request));
    }
}
