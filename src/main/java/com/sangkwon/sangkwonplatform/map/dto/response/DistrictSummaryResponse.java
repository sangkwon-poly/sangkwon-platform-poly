package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.repository.DistrictSummary;

public record DistrictSummaryResponse(
        String trdarCd,
        String trdarNm,
        String signguNm,
        Long salesAmt,
        Long flpop,
        Long storeCnt,
        String changeIx
) {
    public static DistrictSummaryResponse from(DistrictSummary s) {
        return new DistrictSummaryResponse(
                s.getTrdarCd(), s.getTrdarNm(), s.getSignguNm(),
                s.getSalesAmt(), s.getFlpop(), s.getStoreCnt(), s.getChangeIx());
    }
}
