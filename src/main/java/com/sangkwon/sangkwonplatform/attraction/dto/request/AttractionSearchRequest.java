package com.sangkwon.sangkwonplatform.attraction.dto.request;

// 집객시설 조회 필터
public record AttractionSearchRequest(
        String stdrYyquCd,
        String trdarCd
) {
}
