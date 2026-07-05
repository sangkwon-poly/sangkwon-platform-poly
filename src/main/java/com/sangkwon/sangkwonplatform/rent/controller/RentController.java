package com.sangkwon.sangkwonplatform.rent.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.rent.dto.request.RentSearchRequest;
import com.sangkwon.sangkwonplatform.rent.dto.response.RentResponse;
import com.sangkwon.sangkwonplatform.rent.service.RentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rents")
@RequiredArgsConstructor
public class RentController {

    private final RentService rentService;

    @GetMapping
    public ApiResponse<List<RentResponse>> getRents(RentSearchRequest request) {
        return ApiResponse.ok(rentService.getRents(request));
    }
}
