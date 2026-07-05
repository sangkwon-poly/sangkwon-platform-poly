package com.sangkwon.sangkwonplatform.franchisedisclosure.dto.request;

// 정보공개서 조회 필터
public record FranchiseDisclosureSearchRequest(
        String brandNm,
        String corpNm
) {
}
