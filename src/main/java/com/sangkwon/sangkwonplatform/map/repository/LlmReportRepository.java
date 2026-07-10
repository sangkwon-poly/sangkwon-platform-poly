package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.LlmReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LlmReportRepository extends JpaRepository<LlmReport, Long> {

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    // 무료 플랜 월 한도 판정용: 회원이 이번 달 생성한 리포트 수
    long countByMemberIdAndCreatedAtGreaterThanEqual(Long memberId, LocalDateTime from);

    // 최근 생성순. 업종 리포트는 업종 코드로, 상권 전체 리포트는 NULL로 구분한다.
    // 최신 한 건만 필요하므로 Pageable로 조회 행을 제한한다(CLOB까지 전체 이력을 읽지 않게).
    @Query("""
            select r from LlmReport r
            where r.trdarCd = :trdarCd
              and ((:indutyCd is null and r.indutyCd is null) or r.indutyCd = :indutyCd)
            order by r.createdAt desc
            """)
    List<LlmReport> findLatest(@Param("trdarCd") String trdarCd, @Param("indutyCd") String indutyCd, Pageable pageable);

    // 업종명 조회 (INDUTY 엔티티가 따로 없어 여기서 함께 해결)
    @Query(value = "select induty_cd_nm from induty where induty_cd = :indutyCd", nativeQuery = true)
    Optional<String> findIndutyName(@Param("indutyCd") String indutyCd);

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
