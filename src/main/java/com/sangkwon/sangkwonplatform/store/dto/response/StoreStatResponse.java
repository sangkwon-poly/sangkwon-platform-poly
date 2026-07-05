package com.sangkwon.sangkwonplatform.store.dto.response;

import com.sangkwon.sangkwonplatform.store.entity.StoreStat;

import java.math.BigDecimal;

public record StoreStatResponse(
        String stdrYyquCd,
        String trdarCd,
        String indutyCd,
        Long storCo,
        Long similrIndutyStorCo,
        BigDecimal opbizRt,
        BigDecimal clsbizRt,
        Long opbizStorCo,
        Long clsbizStorCo,
        Long frcStorCo
) {
    public static StoreStatResponse from(StoreStat s) {
        return new StoreStatResponse(
                s.getStdrYyquCd(),
                s.getTrdarCd(),
                s.getIndutyCd(),
                s.getStorCo(),
                s.getSimilrIndutyStorCo(),
                s.getOpbizRt(),
                s.getClsbizRt(),
                s.getOpbizStorCo(),
                s.getClsbizStorCo(),
                s.getFrcStorCo()
        );
    }
}
