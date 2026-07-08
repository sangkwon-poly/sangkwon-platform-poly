package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.Trdar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrdarRepository extends JpaRepository<Trdar, String> {

    // 동적 필터
    @Query("""
            select t from Trdar t
            where (:signguCd is null or t.signguCd = :signguCd)
              and (:trdarSeCd is null or t.trdarSeCd = :trdarSeCd)
            order by t.trdarCdNm
            """)
    List<Trdar> search(@Param("signguCd") String signguCd,
                       @Param("trdarSeCd") String trdarSeCd);

    // 지도 폴리곤용 경계 조회. GEO_JSON(CLOB)이 무거워 목록 조회와 분리한다.
    @Query(value = """
            select trdar_cd as "trdarCd", trdar_cd_nm as "trdarNm", geo_json as "geoJson"
            from trdar
            where (:signguCd is null or signgu_cd = :signguCd)
              and (:trdarSeCd is null or trdar_se_cd = :trdarSeCd)
              and (:trdarCd is null or trdar_cd = :trdarCd)
            order by trdar_cd_nm
            """, nativeQuery = true)
    List<DistrictGeo> searchGeo(@Param("signguCd") String signguCd,
                                @Param("trdarSeCd") String trdarSeCd,
                                @Param("trdarCd") String trdarCd);

    // 상권별 분기 요약(매출·유동·점포·변화). quarter가 없으면 sales 최신 분기 기준.
    // 유동·점포·변화는 sales 최신을 상한으로 각 테이블의 최신 분기를 쓴다. 적재 시차 때
    // 전 상권이 빈 값으로 보이는 대신 직전 분기 값을 쓰되 sales보다 앞선 분기는 섞지 않는다.
    // indutyCd를 주면 매출·점포만 그 업종으로 좁힌다 (유동·변화는 업종 구분이 없는 원천).
    @Query(value = """
            select t.trdar_cd as "trdarCd", t.trdar_cd_nm as "trdarNm", t.signgu_nm as "signguNm",
                   t.center_lot as "centerLot", t.center_lat as "centerLat",
                   s.amt as "salesAmt", sp.flpop as "flpop", st.cnt as "storeCnt",
                   tc.chnge_ix as "changeIx", tc.chnge_ix_nm as "changeIxNm",
                   coalesce(:quarter, (select max(stdr_yyqu_cd) from sales)) as "quarter"
            from trdar t
            left join (select trdar_cd, sum(thsmon_selng_amt) amt from sales
                       where stdr_yyqu_cd = coalesce(:quarter, (select max(stdr_yyqu_cd) from sales))
                         and (:indutyCd is null or induty_cd = :indutyCd) group by trdar_cd) s on s.trdar_cd = t.trdar_cd
            left join (select trdar_cd, sum(stor_co) cnt from store_stat
                       where stdr_yyqu_cd = coalesce(:quarter, (select max(stdr_yyqu_cd) from store_stat
                                                                where stdr_yyqu_cd <= (select max(stdr_yyqu_cd) from sales)))
                         and (:indutyCd is null or induty_cd = :indutyCd) group by trdar_cd) st on st.trdar_cd = t.trdar_cd
            left join (select trdar_cd, tot_flpop_co flpop from street_pop
                       where stdr_yyqu_cd = coalesce(:quarter, (select max(stdr_yyqu_cd) from street_pop
                                                                where stdr_yyqu_cd <= (select max(stdr_yyqu_cd) from sales)))) sp on sp.trdar_cd = t.trdar_cd
            left join (select trdar_cd, trdar_chnge_ix chnge_ix, trdar_chnge_ix_nm chnge_ix_nm from trdar_change
                       where stdr_yyqu_cd = coalesce(:quarter, (select max(stdr_yyqu_cd) from trdar_change
                                                                where stdr_yyqu_cd <= (select max(stdr_yyqu_cd) from sales)))) tc on tc.trdar_cd = t.trdar_cd
            where (:signguCd is null or t.signgu_cd = :signguCd)
              and (:trdarSeCd is null or t.trdar_se_cd = :trdarSeCd)
              and (:keyword is null or t.trdar_cd_nm like '%' || :keyword || '%')
            order by s.amt desc nulls last
            """, nativeQuery = true)
    List<DistrictSummary> searchSummary(@Param("signguCd") String signguCd,
                                        @Param("trdarSeCd") String trdarSeCd,
                                        @Param("keyword") String keyword,
                                        @Param("quarter") String quarter,
                                        @Param("indutyCd") String indutyCd);

    // 매출 데이터가 있는 분기 목록 (최신 먼저)
    @Query(value = "select distinct stdr_yyqu_cd from sales order by stdr_yyqu_cd desc", nativeQuery = true)
    List<String> findSalesQuarters();
}
