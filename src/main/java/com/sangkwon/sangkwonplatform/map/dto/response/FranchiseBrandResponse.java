package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.entity.FranchiseBrand;

import java.time.LocalDate;

public record FranchiseBrandResponse(
        String brandMgmtNo,
        String brandNm,
        String corpNm,
        String indutyLclasNm,
        String indutyMlsfcNm,
        LocalDate bizStartDe
) {
    public static FranchiseBrandResponse from(FranchiseBrand e) {
        return new FranchiseBrandResponse(
                e.getBrandMgmtNo(),
                e.getBrandNm(),
                e.getCorpNm(),
                e.getIndutyLclasNm(),
                e.getIndutyMlsfcNm(),
                e.getBizStartDe()
        );
    }
}
