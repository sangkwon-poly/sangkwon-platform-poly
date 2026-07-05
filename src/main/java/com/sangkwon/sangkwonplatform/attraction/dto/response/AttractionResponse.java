package com.sangkwon.sangkwonplatform.attraction.dto.response;

import com.sangkwon.sangkwonplatform.attraction.entity.Attraction;

public record AttractionResponse(
        String stdrYyquCd,
        String trdarCd,
        Long viatrFcltyCo,
        Long subwayStatnCo,
        Long busStopCo,
        Long schoolCo,
        Long hospitalCo,
        Long bankCo
) {
    public static AttractionResponse from(Attraction a) {
        return new AttractionResponse(
                a.getStdrYyquCd(),
                a.getTrdarCd(),
                a.getViatrFcltyCo(),
                a.getSubwayStatnCo(),
                a.getBusStopCo(),
                a.getSchoolCo(),
                a.getHospitalCo(),
                a.getBankCo()
        );
    }
}
