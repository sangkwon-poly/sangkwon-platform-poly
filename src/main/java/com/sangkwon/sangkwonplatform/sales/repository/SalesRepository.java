package com.sangkwon.sangkwonplatform.sales.repository;

import com.sangkwon.sangkwonplatform.sales.entity.Sales;
import com.sangkwon.sangkwonplatform.sales.entity.SalesId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalesRepository extends JpaRepository<Sales, SalesId> {

    // 분기/상권/업종 동적 필터
    @Query("""
            select s from Sales s
            where (:stdrYyquCd is null or s.stdrYyquCd = :stdrYyquCd)
              and (:trdarCd is null or s.trdarCd = :trdarCd)
              and (:indutyCd is null or s.indutyCd = :indutyCd)
            order by s.trdarCd, s.stdrYyquCd
            """)
    List<Sales> search(@Param("stdrYyquCd") String stdrYyquCd,
                       @Param("trdarCd") String trdarCd,
                       @Param("indutyCd") String indutyCd);
}
