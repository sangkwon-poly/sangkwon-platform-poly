package com.sangkwon.sangkwonplatform.residentpop.dto.request;

// 상주인구 조회 필터
public record ResidentPopSearchRequest(
        String stdrYyquCd,
        String trdarCd
) {
}
