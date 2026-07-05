package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.map.dto.request.ResidentPopSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.ResidentPopResponse;
import com.sangkwon.sangkwonplatform.map.service.ResidentPopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resident-pops")
@RequiredArgsConstructor
public class ResidentPopController {

    private final ResidentPopService residentPopService;

    @GetMapping
    public ApiResponse<List<ResidentPopResponse>> getResidentPops(ResidentPopSearchRequest request) {
        return ApiResponse.ok(residentPopService.getResidentPops(request));
    }
}
