package com.sangkwon.sangkwonplatform.industrynewsInsight.repository;

import com.sangkwon.sangkwonplatform.industrynewsInsight.entity.IndustryNewsInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IndustryNewsInsightRepository extends JpaRepository<IndustryNewsInsight, IndustryNewsInsight.PK> {

    @Query(""" 
            select i from IndustryNewsInsight i
            where i.indutyCd = :industyCd
            order by i.yearMonth desc
            """)
    Optional<IndustryNewsInsight>findLatestByIndustyCd(@Param("indutyCd") String indutyCd);
}
