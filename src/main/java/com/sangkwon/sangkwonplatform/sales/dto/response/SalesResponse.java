package com.sangkwon.sangkwonplatform.sales.dto.response;

import com.sangkwon.sangkwonplatform.sales.entity.Sales;

public record SalesResponse(
        String stdrYyquCd,
        String trdarCd,
        String indutyCd,
        Long thsmonSelngAmt,
        Long thsmonSelngCo
) {
    public static SalesResponse from(Sales s) {
        return new SalesResponse(
                s.getStdrYyquCd(),
                s.getTrdarCd(),
                s.getIndutyCd(),
                s.getThsmonSelngAmt(),
                s.getThsmonSelngCo()
        );
    }
}
