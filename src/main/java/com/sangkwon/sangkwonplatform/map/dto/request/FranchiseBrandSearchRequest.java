package com.sangkwon.sangkwonplatform.map.dto.request;

// 프랜차이즈 브랜드 조회 필터
public record FranchiseBrandSearchRequest(
        String brandNm,
        String indutyLclasNm
) {
}
