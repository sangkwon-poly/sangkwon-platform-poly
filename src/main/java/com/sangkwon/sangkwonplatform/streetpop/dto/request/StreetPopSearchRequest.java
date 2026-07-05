package com.sangkwon.sangkwonplatform.streetpop.dto.request;

// 길단위 유동인구 조회 필터
public record StreetPopSearchRequest(
        String stdrYyquCd,
        String trdarCd
) {
}
