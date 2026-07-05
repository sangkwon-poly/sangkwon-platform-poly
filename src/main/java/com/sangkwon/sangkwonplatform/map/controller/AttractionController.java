package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.request.AttractionSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.AttractionResponse;
import com.sangkwon.sangkwonplatform.map.service.AttractionService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/attractions")
@RequiredArgsConstructor
public class AttractionController {

    private final AttractionService attractionService;

    @GetMapping
    public ApiResponse<List<AttractionResponse>> getAttractions(AttractionSearchRequest request) {
        return ApiResponse.ok(attractionService.getAttractions(request));
    }
}
