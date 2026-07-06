package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.repository.DistrictSummary;

import java.math.BigDecimal;

public record DistrictSummaryResponse(
        String trdarCd,
        String trdarNm,
        String signguNm,
        BigDecimal centerLot,
        BigDecimal centerLat,
        Long salesAmt,
        Long flpop,
        Long storeCnt,
        String changeIx,
        String changeIxNm,
        String quarter
) {
    public static DistrictSummaryResponse from(DistrictSummary s) {
        return new DistrictSummaryResponse(
                s.getTrdarCd(), s.getTrdarNm(), s.getSignguNm(),
                s.getCenterLot(), s.getCenterLat(),
                s.getSalesAmt(), s.getFlpop(), s.getStoreCnt(),
                s.getChangeIx(), s.getChangeIxNm(), s.getQuarter());
    }
}
