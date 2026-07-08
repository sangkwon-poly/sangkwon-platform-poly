package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.FranchiseCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FranchiseCountRepository extends JpaRepository<FranchiseCount, Long> {

    // 연도/지역/업종 동적 필터
    @Query("""
            select f from FranchiseCount f
            where (:baseYear is null or f.baseYear = :baseYear)
              and (:areaCd is null or f.areaCd = :areaCd)
              and (:indutyNm is null or f.indutyNm = :indutyNm)
            order by f.areaNm, f.indutyNm
            fetch first 20000 rows only
            """)
    List<FranchiseCount> search(@Param("baseYear") Integer baseYear,
                                @Param("areaCd") String areaCd,
                                @Param("indutyNm") String indutyNm);
}
