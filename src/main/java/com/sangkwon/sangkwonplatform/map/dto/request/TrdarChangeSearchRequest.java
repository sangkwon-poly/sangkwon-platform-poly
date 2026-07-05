package com.sangkwon.sangkwonplatform.map.dto.request;

// 상권 변화지표 조회 필터
public record TrdarChangeSearchRequest(
        String stdrYyquCd,
        String trdarCd
) {
}
