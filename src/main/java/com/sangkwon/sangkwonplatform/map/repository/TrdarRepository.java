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
}
