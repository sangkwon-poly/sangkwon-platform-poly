package com.sangkwon.sangkwonplatform.sales.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.sales.dto.request.SalesSearchRequest;
import com.sangkwon.sangkwonplatform.sales.dto.response.SalesResponse;
import com.sangkwon.sangkwonplatform.sales.service.SalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesService salesService;

    @GetMapping
    public ApiResponse<List<SalesResponse>> getSales(SalesSearchRequest request) {
        return ApiResponse.ok(salesService.getSales(request));
    }
}
