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
            order by trdar_cd_nm
            """, nativeQuery = true)
    List<DistrictGeo> searchGeo(@Param("signguCd") String signguCd,
                                @Param("trdarSeCd") String trdarSeCd);

    // 상권별 최근 분기 요약(매출·유동·점포·변화). 검색 결과·랭킹용.
    @Query(value = """
            select t.trdar_cd as "trdarCd", t.trdar_cd_nm as "trdarNm", t.signgu_nm as "signguNm",
                   s.amt as "salesAmt", sp.flpop as "flpop", st.cnt as "storeCnt", tc.chnge_ix as "changeIx"
            from trdar t
            left join (select trdar_cd, sum(thsmon_selng_amt) amt from sales
                       where stdr_yyqu_cd = (select max(stdr_yyqu_cd) from sales) group by trdar_cd) s on s.trdar_cd = t.trdar_cd
            left join (select trdar_cd, sum(stor_co) cnt from store_stat
                       where stdr_yyqu_cd = (select max(stdr_yyqu_cd) from store_stat) group by trdar_cd) st on st.trdar_cd = t.trdar_cd
            left join (select trdar_cd, tot_flpop_co flpop from street_pop
                       where stdr_yyqu_cd = (select max(stdr_yyqu_cd) from street_pop)) sp on sp.trdar_cd = t.trdar_cd
            left join (select trdar_cd, trdar_chnge_ix chnge_ix from trdar_change
                       where stdr_yyqu_cd = (select max(stdr_yyqu_cd) from trdar_change)) tc on tc.trdar_cd = t.trdar_cd
            where (:signguCd is null or t.signgu_cd = :signguCd)
              and (:trdarSeCd is null or t.trdar_se_cd = :trdarSeCd)
              and (:keyword is null or t.trdar_cd_nm like '%' || :keyword || '%')
            order by s.amt desc nulls last
            """, nativeQuery = true)
    List<DistrictSummary> searchSummary(@Param("signguCd") String signguCd,
                                        @Param("trdarSeCd") String trdarSeCd,
                                        @Param("keyword") String keyword);
}
