package com.sangkwon.sangkwonplatform.residentpop.dto.response;

import com.sangkwon.sangkwonplatform.residentpop.entity.ResidentPop;

public record ResidentPopResponse(
        String stdrYyquCd,
        String trdarCd,
        Long totRepopCo,
        Long mlRepopCo,
        Long fmlRepopCo,
        Long totHshldCo
) {
    public static ResidentPopResponse from(ResidentPop r) {
        return new ResidentPopResponse(
                r.getStdrYyquCd(),
                r.getTrdarCd(),
                r.getTotRepopCo(),
                r.getMlRepopCo(),
                r.getFmlRepopCo(),
                r.getTotHshldCo()
        );
    }
}
