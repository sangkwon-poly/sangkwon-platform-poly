package com.sangkwon.sangkwonplatform.admin.trdar.dto;

import java.util.List;

// 상권 상세 드릴다운: 현재 분기 지표 + 업종별 매출 top + 분기 추이
public record TrdarDetailResponse(
        String trdarCd,
        String trdarNm,
        String signguNm,
        String quarter,
        Metrics metrics,
        List<IndutySales> topInduty,
        List<TrendPoint> trend
) {
    public record Metrics(
            Long salesAmt, Long storeCnt, Long openCnt, Long closeCnt, Long frcCnt,
            Long flpop, Long residentPop, String changeIxNm
    ) {
    }

    public record IndutySales(String indutyCd, String indutyNm, Long amt) {
    }

    public record TrendPoint(String quarter, Long salesAmt, Long storeCnt, Long flpop) {
    }
}
