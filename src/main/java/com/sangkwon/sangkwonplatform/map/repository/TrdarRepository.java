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
}
