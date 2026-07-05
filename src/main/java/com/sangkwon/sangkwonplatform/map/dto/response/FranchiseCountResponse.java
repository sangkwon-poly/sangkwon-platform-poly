package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.entity.FranchiseCount;

import java.math.BigDecimal;

public record FranchiseCountResponse(
        Long frcCntId,
        Integer baseYear,
        String areaCd,
        String areaNm,
        String indutyNm,
        Long frcCo,
        BigDecimal frcRt
) {
    public static FranchiseCountResponse from(FranchiseCount f) {
        return new FranchiseCountResponse(
                f.getFrcCntId(),
                f.getBaseYear(),
                f.getAreaCd(),
                f.getAreaNm(),
                f.getIndutyNm(),
                f.getFrcCo(),
                f.getFrcRt()
        );
    }
}
