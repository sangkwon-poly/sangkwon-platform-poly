package com.sangkwon.sangkwonplatform.admin.ops.repository;

import com.sangkwon.sangkwonplatform.admin.ops.entity.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {

    List<ApiUsageLog> findByUsageDateOrderByApiName(LocalDate usageDate);

    // 사용량 이력 보존 정리: 오래된 일자별 집계 행 삭제(무한 증식 방지)
    @Transactional
    @Modifying
    @Query("delete from ApiUsageLog a where a.usageDate < :cutoff")
    int deleteByUsageDateBefore(@Param("cutoff") LocalDate cutoff);

    // 오늘 집계 행이 없으면 만들고 있으면 +1. UNIQUE(API_NAME, USAGE_DATE) 위에서 MERGE라 동시 호출에도 유실이 없다.
    @Modifying
    @Query(value = """
            MERGE INTO API_USAGE_LOG t
            USING (SELECT :apiName AS API_NAME, TRUNC(SYSDATE) AS USAGE_DATE FROM DUAL) s
            ON (t.API_NAME = s.API_NAME AND t.USAGE_DATE = s.USAGE_DATE)
            WHEN MATCHED THEN UPDATE SET t.CALL_CNT = t.CALL_CNT + 1, t.UPDATED_AT = SYSTIMESTAMP
            WHEN NOT MATCHED THEN INSERT (API_NAME, USAGE_DATE, CALL_CNT, DAILY_LIMIT, CREATED_AT, UPDATED_AT)
            VALUES (s.API_NAME, s.USAGE_DATE, 1, :dailyLimit, SYSTIMESTAMP, SYSTIMESTAMP)
            """, nativeQuery = true)
    int increaseTodayCall(@Param("apiName") String apiName, @Param("dailyLimit") long dailyLimit);

    // 같은 트랜잭션에서 MERGE 직후 읽으면 그 행이 잠겨 있어 동시 요청과 어긋난 값을 볼 일이 없다
    @Query(value = "SELECT CALL_CNT FROM API_USAGE_LOG WHERE API_NAME = :apiName AND USAGE_DATE = TRUNC(SYSDATE)",
            nativeQuery = true)
    Optional<Long> findTodayCallCnt(@Param("apiName") String apiName);
}
