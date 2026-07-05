package com.sangkwon.sangkwonplatform.rent.repository;

import com.sangkwon.sangkwonplatform.rent.entity.CommercialRent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommercialRentRepository extends JpaRepository<CommercialRent, Long> {

    // 지역/지표/유형/분기 동적 필터
    @Query("""
            select r from CommercialRent r
            where (:regionCd is null or r.regionCd = :regionCd)
              and (:metricCd is null or r.metricCd = :metricCd)
              and (:rlstTyCd is null or r.rlstTyCd = :rlstTyCd)
              and (:stdrYyquCd is null or r.stdrYyquCd = :stdrYyquCd)
            order by r.regionNm, r.stdrYyquCd
            """)
    List<CommercialRent> search(@Param("regionCd") String regionCd,
                                @Param("metricCd") String metricCd,
                                @Param("rlstTyCd") String rlstTyCd,
                                @Param("stdrYyquCd") String stdrYyquCd);
}
