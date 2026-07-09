package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.StreetPop;
import com.sangkwon.sangkwonplatform.map.entity.StreetPopId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StreetPopRepository extends JpaRepository<StreetPop, StreetPopId> {

    // 분기/상권 동적 필터
    @Query("""
            select s from StreetPop s
            where (:stdrYyquCd is null or s.stdrYyquCd = :stdrYyquCd)
              and (:trdarCd is null or s.trdarCd = :trdarCd)
            order by s.trdarCd, s.stdrYyquCd
            fetch first 20000 rows only
            """)
    List<StreetPop> search(@Param("stdrYyquCd") String stdrYyquCd,
                           @Param("trdarCd") String trdarCd);
}
