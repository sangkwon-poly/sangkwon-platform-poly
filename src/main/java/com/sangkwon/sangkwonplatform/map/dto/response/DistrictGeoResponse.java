package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.repository.DistrictGeo;

import java.sql.Clob;
import java.sql.SQLException;

public record DistrictGeoResponse(
        String trdarCd,
        String trdarNm,
        String geoJson
) {
    public static DistrictGeoResponse from(DistrictGeo g) {
        return new DistrictGeoResponse(g.getTrdarCd(), g.getTrdarNm(), read(g.getGeoJson()));
    }

    // CLOB을 문자열로 (조회 트랜잭션 안에서 읽는다)
    private static String read(Clob clob) {
        if (clob == null) {
            return null;
        }
        try {
            return clob.getSubString(1, (int) clob.length());
        } catch (SQLException e) {
            throw new IllegalStateException("GEO_JSON 읽기 실패", e);
        }
    }
}
