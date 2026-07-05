package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.FranchiseBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FranchiseBrandRepository extends JpaRepository<FranchiseBrand, String> {

    // 브랜드명/업종 대분류 동적 필터
    @Query("""
            select e from FranchiseBrand e
            where (:brandNm is null or e.brandNm like concat('%', :brandNm, '%'))
              and (:indutyLclasNm is null or e.indutyLclasNm = :indutyLclasNm)
            order by e.brandNm, e.brandMgmtNo
            """)
    List<FranchiseBrand> search(@Param("brandNm") String brandNm,
                                @Param("indutyLclasNm") String indutyLclasNm);
}
