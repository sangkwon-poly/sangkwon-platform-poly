package com.sangkwon.sangkwonplatform.industrynewsInsight.repository;

import com.sangkwon.sangkwonplatform.industrynewsInsight.entity.IndustryNewsInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndustryNewsInsightRepository extends JpaRepository<IndustryNewsInsight, IndustryNewsInsight.PK> {

    // 같은 업종에 월별 행이 쌓이므로 최신 한 건만 집는다(전체를 Optional에 매핑하면 다중 행에서 예외)
    Optional<IndustryNewsInsight> findFirstByIndutyCdOrderByYearMonthDesc(String indutyCd);
}
