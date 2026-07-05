package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.entity.StreetPop;

public record StreetPopResponse(
        String stdrYyquCd,
        String trdarCd,
        Long totFlpopCo,
        Long mlFlpopCo,
        Long fmlFlpopCo
) {
    public static StreetPopResponse from(StreetPop s) {
        return new StreetPopResponse(
                s.getStdrYyquCd(),
                s.getTrdarCd(),
                s.getTotFlpopCo(),
                s.getMlFlpopCo(),
                s.getFmlFlpopCo()
        );
    }
}
