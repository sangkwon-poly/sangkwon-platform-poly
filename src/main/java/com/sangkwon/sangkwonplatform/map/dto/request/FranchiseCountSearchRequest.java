package com.sangkwon.sangkwonplatform.map.dto.request;

// 가맹점수 조회 필터
public record FranchiseCountSearchRequest(
        Integer baseYear,
        String areaCd,
        String indutyNm
) {
}
