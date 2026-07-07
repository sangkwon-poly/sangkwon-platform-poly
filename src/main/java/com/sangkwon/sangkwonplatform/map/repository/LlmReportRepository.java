package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.LlmReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LlmReportRepository extends JpaRepository<LlmReport, Long> {

    Optional<LlmReport> findFirstByTrdarCdOrderByCreatedAtDesc(String trdarCd);

    // 리포트 프롬프트용: 해당 분기 매출 상위 업종 5개 (이름 포함)
    @Query(value = """
            select i.induty_cd_nm as "name", s.amt as "amt"
            from (select induty_cd, sum(thsmon_selng_amt) amt from sales
                  where trdar_cd = :trdarCd and stdr_yyqu_cd = :quarter
                  group by induty_cd order by amt desc fetch first 5 rows only) s
            join induty i on i.induty_cd = s.induty_cd
            order by s.amt desc
            """, nativeQuery = true)
    List<TopInduty> findTopIndusties(@Param("trdarCd") String trdarCd, @Param("quarter") String quarter);

    interface TopInduty {
        String getName();

        Long getAmt();
    }
}
