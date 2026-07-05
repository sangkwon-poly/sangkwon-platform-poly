package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.entity.ResidentPop;

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
