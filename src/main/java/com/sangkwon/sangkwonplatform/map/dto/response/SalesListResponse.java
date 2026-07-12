package com.sangkwon.sangkwonplatform.map.dto.response;

import java.util.List;

// 추정매출 목록 응답. 상한 잘림 여부를 함께 내려 완전한 결과로 오인하지 않게 한다.
public record SalesListResponse(
        List<SalesResponse> items,
        long returned,
        int limit,
        boolean truncated
) {
}
