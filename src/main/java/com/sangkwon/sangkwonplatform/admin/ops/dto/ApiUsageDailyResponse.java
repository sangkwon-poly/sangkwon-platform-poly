package com.sangkwon.sangkwonplatform.admin.ops.dto;

import java.time.LocalDate;

// 일자별 외부 API 총 호출량 한 점. 집계가 없는 날도 0으로 채워 연속 추이로 보여준다.
public record ApiUsageDailyResponse(
        LocalDate date,
        long totalCalls
) {
}
