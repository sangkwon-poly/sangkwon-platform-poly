package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.MapConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 카카오 JS 키를 프론트에 전달 (키를 정적 파일에 박지 않기 위함)
@RestController
@RequestMapping("/api/map/config")
public class MapConfigController {

    private final String kakaoJsKey;

    public MapConfigController(@Value("${kakao.map.js-key}") String kakaoJsKey) {
        this.kakaoJsKey = kakaoJsKey;
    }

    @GetMapping
    public ApiResponse<MapConfigResponse> config() {
        return ApiResponse.ok(new MapConfigResponse(kakaoJsKey));
    }
}
