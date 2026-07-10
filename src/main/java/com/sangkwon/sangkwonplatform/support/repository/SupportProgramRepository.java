package com.sangkwon.sangkwonplatform.support.repository;

import com.sangkwon.sangkwonplatform.support.entity.SupportProgram;
import com.sangkwon.sangkwonplatform.support.entity.SupportProgramId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface SupportProgramRepository extends JpaRepository<SupportProgram, SupportProgramId> {

    // 목록: 노출(Y)만. 필터는 값이 없으면 통과. 정렬은 마감임박순(마감일 없는 상시/무기한은 뒤).
    // 상세필터(창업기간/연령)는 K-Startup 상세에만 있어, 켜지면 기업마당은 빠진다.
    // includeUnknown=1이면 상세필터가 걸려도 기업마당을 다시 포함한다.
    // 유형 필터: typeMode 0=전체, 1=탭 원본값 IN, 2=기타(이름 있는 값들의 NOT IN).
    // 기관명(org)은 K-Startup 상세의 주관기관을 상관 서브쿼리로 붙인다(기업마당은 NULL).
    @Query(value = """
            select p.source_cd       as "sourceCd",
                   p.program_id      as "programId",
                   p.title           as "title",
                   p.program_type    as "programType",
                   p.region          as "region",
                   p.apply_bgng_de   as "applyBgngDe",
                   p.apply_end_de    as "applyEndDe",
                   p.apply_period_raw as "applyPeriodRaw",
                   p.recruit_yn      as "recruitYn",
                   p.detail_url      as "detailUrl",
                   (select d.sprv_inst from support_program_kstartup_detail d
                     where d.source_cd = p.source_cd and d.program_id = p.program_id) as "org"
            from support_program p
            where p.is_visible = 'Y'
              and (:source is null or p.source_cd = :source)
              and (:region is null
                   or (:region = 'seoul' and p.region = '서울')
                   or (:region = 'nation' and p.region = '전국'))
              and (:targetLike is null or lower(p.target) like :targetLike)
              and (:qLike is null or lower(p.title) like :qLike)
              and (:typeMode = 0
                   or (:typeMode = 1 and p.program_type in (:typeRaws))
                   or (:typeMode = 2 and (p.program_type is null or p.program_type not in (:typeRaws))))
              and (:recruitingOnly = 0
                   or (p.apply_end_de is not null and p.apply_end_de >= :today)
                   or (p.apply_end_de is null and (p.recruit_yn = 'Y' or p.apply_period_raw is not null)))
              and (:foundingLike is null
                   or exists (select 1 from support_program_kstartup_detail df
                               where df.source_cd = p.source_cd and df.program_id = p.program_id
                                 and df.biz_enyy like :foundingLike)
                   or (:includeUnknown = 1 and p.source_cd = 'BIZINFO'))
              and (:ageLike is null
                   or exists (select 1 from support_program_kstartup_detail da
                               where da.source_cd = p.source_cd and da.program_id = p.program_id
                                 and da.biz_trgt_age like :ageLike)
                   or (:includeUnknown = 1 and p.source_cd = 'BIZINFO'))
            order by case when p.apply_end_de is null then 1 else 0 end, p.apply_end_de, p.program_id
            """,
            countQuery = """
            select count(*)
            from support_program p
            where p.is_visible = 'Y'
              and (:source is null or p.source_cd = :source)
              and (:region is null
                   or (:region = 'seoul' and p.region = '서울')
                   or (:region = 'nation' and p.region = '전국'))
              and (:targetLike is null or lower(p.target) like :targetLike)
              and (:qLike is null or lower(p.title) like :qLike)
              and (:typeMode = 0
                   or (:typeMode = 1 and p.program_type in (:typeRaws))
                   or (:typeMode = 2 and (p.program_type is null or p.program_type not in (:typeRaws))))
              and (:recruitingOnly = 0
                   or (p.apply_end_de is not null and p.apply_end_de >= :today)
                   or (p.apply_end_de is null and (p.recruit_yn = 'Y' or p.apply_period_raw is not null)))
              and (:foundingLike is null
                   or exists (select 1 from support_program_kstartup_detail df
                               where df.source_cd = p.source_cd and df.program_id = p.program_id
                                 and df.biz_enyy like :foundingLike)
                   or (:includeUnknown = 1 and p.source_cd = 'BIZINFO'))
              and (:ageLike is null
                   or exists (select 1 from support_program_kstartup_detail da
                               where da.source_cd = p.source_cd and da.program_id = p.program_id
                                 and da.biz_trgt_age like :ageLike)
                   or (:includeUnknown = 1 and p.source_cd = 'BIZINFO'))
            """,
            nativeQuery = true)
    Page<SupportProgramListRow> search(@Param("source") String source,
                                       @Param("region") String region,
                                       @Param("targetLike") String targetLike,
                                       @Param("qLike") String qLike,
                                       @Param("typeMode") int typeMode,
                                       @Param("typeRaws") List<String> typeRaws,
                                       @Param("recruitingOnly") int recruitingOnly,
                                       @Param("today") LocalDate today,
                                       @Param("foundingLike") String foundingLike,
                                       @Param("ageLike") String ageLike,
                                       @Param("includeUnknown") int includeUnknown,
                                       Pageable pageable);

    // 유형 탭 카운트: 유형 조건만 뺀 나머지 필터를 적용해 원본값별 건수를 센다(서비스에서 탭으로 합산).
    @Query(value = """
            select p.program_type as "programType", count(*) as "cnt"
            from support_program p
            where p.is_visible = 'Y'
              and (:source is null or p.source_cd = :source)
              and (:region is null
                   or (:region = 'seoul' and p.region = '서울')
                   or (:region = 'nation' and p.region = '전국'))
              and (:targetLike is null or lower(p.target) like :targetLike)
              and (:qLike is null or lower(p.title) like :qLike)
              and (:recruitingOnly = 0
                   or (p.apply_end_de is not null and p.apply_end_de >= :today)
                   or (p.apply_end_de is null and (p.recruit_yn = 'Y' or p.apply_period_raw is not null)))
              and (:foundingLike is null
                   or exists (select 1 from support_program_kstartup_detail df
                               where df.source_cd = p.source_cd and df.program_id = p.program_id
                                 and df.biz_enyy like :foundingLike)
                   or (:includeUnknown = 1 and p.source_cd = 'BIZINFO'))
              and (:ageLike is null
                   or exists (select 1 from support_program_kstartup_detail da
                               where da.source_cd = p.source_cd and da.program_id = p.program_id
                                 and da.biz_trgt_age like :ageLike)
                   or (:includeUnknown = 1 and p.source_cd = 'BIZINFO'))
            group by p.program_type
            """, nativeQuery = true)
    List<TypeCountRow> typeCounts(@Param("source") String source,
                                  @Param("region") String region,
                                  @Param("targetLike") String targetLike,
                                  @Param("qLike") String qLike,
                                  @Param("recruitingOnly") int recruitingOnly,
                                  @Param("today") LocalDate today,
                                  @Param("foundingLike") String foundingLike,
                                  @Param("ageLike") String ageLike,
                                  @Param("includeUnknown") int includeUnknown);

    // 상세필터로 빠지는 기업마당 건수(배너 안내용). 상세필터/출처 무관 나머지 필터만 적용.
    @Query(value = """
            select count(*)
            from support_program p
            where p.is_visible = 'Y' and p.source_cd = 'BIZINFO'
              and (:region is null
                   or (:region = 'seoul' and p.region = '서울')
                   or (:region = 'nation' and p.region = '전국'))
              and (:targetLike is null or lower(p.target) like :targetLike)
              and (:qLike is null or lower(p.title) like :qLike)
              and (:typeMode = 0
                   or (:typeMode = 1 and p.program_type in (:typeRaws))
                   or (:typeMode = 2 and (p.program_type is null or p.program_type not in (:typeRaws))))
              and (:recruitingOnly = 0
                   or (p.apply_end_de is not null and p.apply_end_de >= :today)
                   or (p.apply_end_de is null and (p.recruit_yn = 'Y' or p.apply_period_raw is not null)))
            """, nativeQuery = true)
    long countBizinfoExcludable(@Param("region") String region,
                                @Param("targetLike") String targetLike,
                                @Param("qLike") String qLike,
                                @Param("typeMode") int typeMode,
                                @Param("typeRaws") List<String> typeRaws,
                                @Param("recruitingOnly") int recruitingOnly,
                                @Param("today") LocalDate today);

    // 관리자용 목록: 숨김(IS_VISIBLE='N') 포함 전체. 노출여부/출처/유형/상태/제목 필터.
    // 정렬은 진행중(상시 포함)을 먼저, 그 안에서 최근 등록순으로. 오래된 마감 공고가 위를 덮지 않게 한다.
    @Query(value = """
            select p.source_cd       as "sourceCd",
                   p.program_id      as "programId",
                   p.title           as "title",
                   p.program_type    as "programType",
                   p.region          as "region",
                   p.apply_bgng_de   as "applyBgngDe",
                   p.apply_end_de    as "applyEndDe",
                   p.apply_period_raw as "applyPeriodRaw",
                   p.recruit_yn      as "recruitYn",
                   p.is_visible      as "isVisible",
                   p.detail_url      as "detailUrl"
            from support_program p
            where (:visibility is null or p.is_visible = :visibility)
              and (:source is null or p.source_cd = :source)
              and (:typeMode = 0
                   or (:typeMode = 1 and p.program_type in (:typeRaws))
                   or (:typeMode = 2 and (p.program_type is null or p.program_type not in (:typeRaws))))
              and (:qLike is null or lower(p.title) like :qLike)
              and (:status is null
                   or (:status = 'OPEN' and ((p.apply_end_de is not null and p.apply_end_de >= :today)
                        or (p.apply_end_de is null and (p.recruit_yn = 'Y' or p.apply_period_raw is not null))))
                   or (:status = 'CLOSED' and not ((p.apply_end_de is not null and p.apply_end_de >= :today)
                        or (p.apply_end_de is null and (p.recruit_yn = 'Y' or p.apply_period_raw is not null))))
                   or (:status = 'CLOSING' and p.apply_end_de is not null
                        and p.apply_end_de >= :today and p.apply_end_de <= :today + 7))
            order by case when (p.apply_end_de is not null and p.apply_end_de >= :today)
                               or (p.apply_end_de is null and (p.recruit_yn = 'Y' or p.apply_period_raw is not null))
                          then 0 else 1 end,
                     case when p.apply_end_de is not null and p.apply_end_de >= :today then p.apply_end_de end asc nulls last,
                     case when p.apply_end_de is not null and p.apply_end_de < :today then p.apply_end_de end desc nulls last,
                     p.program_id
            """,
            countQuery = """
            select count(*)
            from support_program p
            where (:visibility is null or p.is_visible = :visibility)
              and (:source is null or p.source_cd = :source)
              and (:typeMode = 0
                   or (:typeMode = 1 and p.program_type in (:typeRaws))
                   or (:typeMode = 2 and (p.program_type is null or p.program_type not in (:typeRaws))))
              and (:qLike is null or lower(p.title) like :qLike)
              and (:status is null
                   or (:status = 'OPEN' and ((p.apply_end_de is not null and p.apply_end_de >= :today)
                        or (p.apply_end_de is null and (p.recruit_yn = 'Y' or p.apply_period_raw is not null))))
                   or (:status = 'CLOSED' and not ((p.apply_end_de is not null and p.apply_end_de >= :today)
                        or (p.apply_end_de is null and (p.recruit_yn = 'Y' or p.apply_period_raw is not null))))
                   or (:status = 'CLOSING' and p.apply_end_de is not null
                        and p.apply_end_de >= :today and p.apply_end_de <= :today + 7))
            """, nativeQuery = true)
    Page<AdminSupportListRow> adminSearch(@Param("visibility") String visibility,
                                          @Param("source") String source,
                                          @Param("typeMode") int typeMode,
                                          @Param("typeRaws") List<String> typeRaws,
                                          @Param("qLike") String qLike,
                                          @Param("status") String status,
                                          @Param("today") LocalDate today,
                                          Pageable pageable);

    // 관리자 요약: 전체·진행중·마감·노출·숨김·출처별
    @Query(value = """
            select count(*) as "total",
                   sum(case when (apply_end_de is not null and apply_end_de >= :today)
                             or (apply_end_de is null and (recruit_yn = 'Y' or apply_period_raw is not null))
                        then 1 else 0 end) as "open",
                   sum(case when not ((apply_end_de is not null and apply_end_de >= :today)
                             or (apply_end_de is null and (recruit_yn = 'Y' or apply_period_raw is not null)))
                        then 1 else 0 end) as "closed",
                   sum(case when is_visible = 'Y' then 1 else 0 end) as "visible",
                   sum(case when is_visible = 'N' then 1 else 0 end) as "hidden",
                   sum(case when source_cd = 'BIZINFO' then 1 else 0 end) as "bizinfo",
                   sum(case when source_cd = 'KSTARTUP' then 1 else 0 end) as "kstartup"
            from support_program
            """, nativeQuery = true)
    AdminSupportCounts adminCounts(@Param("today") LocalDate today);

    // 목록 카드용 투영
    interface SupportProgramListRow {
        String getSourceCd();

        String getProgramId();

        String getTitle();

        String getProgramType();

        String getRegion();

        // Oracle DATE는 네이티브 투영에서 LocalDateTime으로 넘어와 서비스에서 날짜만 취한다
        LocalDateTime getApplyBgngDe();

        LocalDateTime getApplyEndDe();

        String getApplyPeriodRaw();

        String getRecruitYn();

        String getDetailUrl();

        String getOrg();
    }

    interface TypeCountRow {
        String getProgramType();

        long getCnt();
    }

    interface AdminSupportListRow {
        String getSourceCd();

        String getProgramId();

        String getTitle();

        String getProgramType();

        String getRegion();

        LocalDateTime getApplyBgngDe();

        LocalDateTime getApplyEndDe();

        String getApplyPeriodRaw();

        String getRecruitYn();

        String getIsVisible();

        String getDetailUrl();
    }

    interface AdminSupportCounts {
        long getTotal();

        long getOpen();

        long getClosed();

        long getVisible();

        long getHidden();

        long getBizinfo();

        long getKstartup();
    }
}
