package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.entity.Apt;

import java.math.BigDecimal;

public record AptResponse(
        String stdrYyquCd,
        String trdarCd,
        Long aptComplxCo,
        Long aptHshldCo,
        BigDecimal avrgArea,
        Long avrgMrktPrc
) {
    public static AptResponse from(Apt a) {
        return new AptResponse(
                a.getStdrYyquCd(),
                a.getTrdarCd(),
                a.getAptComplxCo(),
                a.getAptHshldCo(),
                a.getAvrgArea(),
                a.getAvrgMrktPrc()
        );
    }
}
