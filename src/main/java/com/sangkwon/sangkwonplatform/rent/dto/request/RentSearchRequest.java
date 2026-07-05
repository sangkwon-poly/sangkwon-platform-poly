package com.sangkwon.sangkwonplatform.rent.dto.request;

// 임대 지표 조회 필터
public record RentSearchRequest(
        String regionCd,
        String metricCd,
        String rlstTyCd,
        String stdrYyquCd
) {
}
