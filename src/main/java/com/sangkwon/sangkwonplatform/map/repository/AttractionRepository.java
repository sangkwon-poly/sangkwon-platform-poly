package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.Attraction;
import com.sangkwon.sangkwonplatform.map.entity.AttractionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttractionRepository extends JpaRepository<Attraction, AttractionId> {

    // 분기/상권 동적 필터
    @Query("""
            select a from Attraction a
            where (:stdrYyquCd is null or a.stdrYyquCd = :stdrYyquCd)
              and (:trdarCd is null or a.trdarCd = :trdarCd)
            order by a.trdarCd, a.stdrYyquCd
            """)
    List<Attraction> search(@Param("stdrYyquCd") String stdrYyquCd,
                            @Param("trdarCd") String trdarCd);
}
