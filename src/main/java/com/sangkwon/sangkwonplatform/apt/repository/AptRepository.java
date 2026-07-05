package com.sangkwon.sangkwonplatform.apt.repository;

import com.sangkwon.sangkwonplatform.apt.entity.Apt;
import com.sangkwon.sangkwonplatform.apt.entity.AptId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AptRepository extends JpaRepository<Apt, AptId> {

    // 분기/상권 동적 필터
    @Query("""
            select a from Apt a
            where (:stdrYyquCd is null or a.stdrYyquCd = :stdrYyquCd)
              and (:trdarCd is null or a.trdarCd = :trdarCd)
            order by a.trdarCd, a.stdrYyquCd
            """)
    List<Apt> search(@Param("stdrYyquCd") String stdrYyquCd,
                     @Param("trdarCd") String trdarCd);
}
