package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.entity.TrdarChange;

import java.math.BigDecimal;

public record TrdarChangeResponse(
        String stdrYyquCd,
        String trdarCd,
        String trdarChngeIx,
        String trdarChngeIxNm,
        BigDecimal oprSaleMtAvrg,
        BigDecimal clsSaleMtAvrg
) {
    public static TrdarChangeResponse from(TrdarChange t) {
        return new TrdarChangeResponse(
                t.getStdrYyquCd(),
                t.getTrdarCd(),
                t.getTrdarChngeIx(),
                t.getTrdarChngeIxNm(),
                t.getOprSaleMtAvrg(),
                t.getClsSaleMtAvrg()
        );
    }
}
