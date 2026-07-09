package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.FranchiseDisclosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FranchiseDisclosureRepository extends JpaRepository<FranchiseDisclosure, String> {

    // 브랜드명/법인명 동적 필터
    @Query("""
            select f from FranchiseDisclosure f
            where (:brandNm is null or f.brandNm like concat('%', :brandNm, '%'))
              and (:corpNm is null or f.corpNm = :corpNm)
            order by f.brandNm, f.corpNm
            fetch first 20000 rows only
            """)
    List<FranchiseDisclosure> search(@Param("brandNm") String brandNm,
                                     @Param("corpNm") String corpNm);
}
