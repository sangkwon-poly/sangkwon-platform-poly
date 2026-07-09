package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.ResidentPop;
import com.sangkwon.sangkwonplatform.map.entity.ResidentPopId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResidentPopRepository extends JpaRepository<ResidentPop, ResidentPopId> {

    // 분기/상권 동적 필터
    @Query("""
            select r from ResidentPop r
            where (:stdrYyquCd is null or r.stdrYyquCd = :stdrYyquCd)
              and (:trdarCd is null or r.trdarCd = :trdarCd)
            order by r.trdarCd, r.stdrYyquCd
            fetch first 20000 rows only
            """)
    List<ResidentPop> search(@Param("stdrYyquCd") String stdrYyquCd,
                             @Param("trdarCd") String trdarCd);
}
