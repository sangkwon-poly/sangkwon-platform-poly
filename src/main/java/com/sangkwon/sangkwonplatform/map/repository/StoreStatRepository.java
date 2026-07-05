package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.StoreStat;
import com.sangkwon.sangkwonplatform.map.entity.StoreStatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoreStatRepository extends JpaRepository<StoreStat, StoreStatId> {

    // 분기/상권/업종 동적 필터
    @Query("""
            select s from StoreStat s
            where (:stdrYyquCd is null or s.stdrYyquCd = :stdrYyquCd)
              and (:trdarCd is null or s.trdarCd = :trdarCd)
              and (:indutyCd is null or s.indutyCd = :indutyCd)
            order by s.trdarCd, s.stdrYyquCd, s.indutyCd
            """)
    List<StoreStat> search(@Param("stdrYyquCd") String stdrYyquCd,
                           @Param("trdarCd") String trdarCd,
                           @Param("indutyCd") String indutyCd);
}
