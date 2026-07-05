package com.sangkwon.sangkwonplatform.map.dto.request;

// 상권 조회 필터
public record DistrictSearchRequest(
        String signguCd,
        String trdarSeCd
) {
}
