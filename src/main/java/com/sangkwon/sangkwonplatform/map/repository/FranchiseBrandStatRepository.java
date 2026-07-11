package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.FranchiseBrandStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FranchiseBrandStatRepository extends JpaRepository<FranchiseBrandStat, Long> {

    // 적재가 업종당 5개만 남기지만, 조회도 상한을 걸어 정렬 순서를 보장한다(가맹점수 동수는 브랜드명순)
    List<FranchiseBrandStat> findTop5ByIndutyCdOrderByFrcsCntDescBrandNmAsc(String indutyCd);
}
