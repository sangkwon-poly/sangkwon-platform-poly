package com.sangkwon.sangkwonplatform.map.dto.request;

// 아파트 조회 필터
public record AptSearchRequest(
        String stdrYyquCd,
        String trdarCd
) {
}
