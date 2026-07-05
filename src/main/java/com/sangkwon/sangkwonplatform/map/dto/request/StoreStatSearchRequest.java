package com.sangkwon.sangkwonplatform.map.dto.request;

// 점포 통계 조회 필터
public record StoreStatSearchRequest(
        String stdrYyquCd,
        String trdarCd,
        String indutyCd
) {
}
