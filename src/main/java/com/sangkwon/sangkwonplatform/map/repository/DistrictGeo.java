package com.sangkwon.sangkwonplatform.map.repository;

import java.sql.Clob;

// 상권 경계 조회 결과 (native 쿼리 투영, GEO_JSON은 CLOB)
public interface DistrictGeo {

    String getTrdarCd();

    String getTrdarNm();

    Clob getGeoJson();
}
