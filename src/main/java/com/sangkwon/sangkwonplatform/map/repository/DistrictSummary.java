package com.sangkwon.sangkwonplatform.map.repository;

import java.math.BigDecimal;

// 상권 요약 조회 결과 (native 집계 쿼리 투영)
public interface DistrictSummary {

    String getTrdarCd();

    String getTrdarNm();

    String getSignguNm();

    BigDecimal getCenterLot();

    BigDecimal getCenterLat();

    Long getSalesAmt();

    Long getFlpop();

    Long getStoreCnt();

    String getChangeIx();

    String getChangeIxNm();

    String getQuarter();
}
