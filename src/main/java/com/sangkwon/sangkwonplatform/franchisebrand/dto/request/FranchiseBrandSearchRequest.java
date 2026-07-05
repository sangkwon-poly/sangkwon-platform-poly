package com.sangkwon.sangkwonplatform.franchisebrand.dto.request;

// 프랜차이즈 브랜드 조회 필터
public record FranchiseBrandSearchRequest(
        String brandNm,
        String indutyLclasNm
) {
}
