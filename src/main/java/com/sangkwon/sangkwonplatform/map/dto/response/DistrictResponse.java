package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.entity.Trdar;

import java.math.BigDecimal;

public record DistrictResponse(
        String trdarCd,
        String trdarNm,
        String seCd,
        String seNm,
        String signguNm,
        BigDecimal centerLot,
        BigDecimal centerLat
) {
    public static DistrictResponse from(Trdar t) {
        return new DistrictResponse(
                t.getTrdarCd(),
                t.getTrdarCdNm(),
                t.getTrdarSeCd(),
                t.getTrdarSeCdNm(),
                t.getSignguNm(),
                t.getCenterLot(),
                t.getCenterLat()
        );
    }
}
