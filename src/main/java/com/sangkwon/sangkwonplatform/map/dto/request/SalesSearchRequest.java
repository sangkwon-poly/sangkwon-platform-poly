package com.sangkwon.sangkwonplatform.map.dto.request;

// 추정매출 조회 필터
public record SalesSearchRequest(
        String stdrYyquCd,
        String trdarCd,
        String indutyCd
) {
}
