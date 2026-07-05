package com.sangkwon.sangkwonplatform.map.repository;

import com.sangkwon.sangkwonplatform.map.entity.TrdarChange;
import com.sangkwon.sangkwonplatform.map.entity.TrdarChangeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrdarChangeRepository extends JpaRepository<TrdarChange, TrdarChangeId> {

    // 분기/상권 동적 필터
    @Query("""
            select t from TrdarChange t
            where (:stdrYyquCd is null or t.stdrYyquCd = :stdrYyquCd)
              and (:trdarCd is null or t.trdarCd = :trdarCd)
            order by t.trdarCd, t.stdrYyquCd
            """)
    List<TrdarChange> search(@Param("stdrYyquCd") String stdrYyquCd,
                             @Param("trdarCd") String trdarCd);
}
