package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.entity.FranchiseBrandStat;

public record FranchiseBrandStatResponse(
        String brandNm,
        String corpNm,
        Integer baseYear,
        Long frcsCnt,
        Long avgSalesAmt,
        Long newFrcsRgsCnt
) {
    public static FranchiseBrandStatResponse from(FranchiseBrandStat e) {
        return new FranchiseBrandStatResponse(
                e.getBrandNm(),
                e.getCorpNm(),
                e.getBaseYear(),
                e.getFrcsCnt(),
                e.getAvgSalesAmt(),
                e.getNewFrcsRgsCnt()
        );
    }
}
