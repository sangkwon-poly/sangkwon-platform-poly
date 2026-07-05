package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.entity.CommercialRent;

import java.math.BigDecimal;

public record RentResponse(
        String regionCd,
        String regionNm,
        String rlstTyCd,
        String metricCd,
        String metricNm,
        String stdrYyquCd,
        BigDecimal metricValue,
        String uom
) {
    public static RentResponse from(CommercialRent r) {
        return new RentResponse(
                r.getRegionCd(),
                r.getRegionNm(),
                r.getRlstTyCd(),
                r.getMetricCd(),
                r.getMetricNm(),
                r.getStdrYyquCd(),
                r.getMetricValue(),
                r.getUom()
        );
    }
}
