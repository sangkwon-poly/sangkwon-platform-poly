package com.sangkwon.sangkwonplatform.global.batch;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BatchJobLogRepository extends JpaRepository<BatchJobLog, Long> {

    List<BatchJobLog> findByOrderByStartedAtDesc(Limit limit);

    // 카탈로그: 데이터셋별 최근 실행 1건
    Optional<BatchJobLog> findFirstByDatasetCdOrderByStartedAtDesc(String datasetCd);

    // 상세: 데이터셋별 최근 실행 이력
    List<BatchJobLog> findByDatasetCdOrderByStartedAtDesc(String datasetCd, Limit limit);

    // 중복 트리거 방지: 같은 데이터셋이 진행 중인지
    boolean existsByDatasetCdAndStatus(String datasetCd, BatchStatus status);

    // 장기 실행 중에도 생존 신호를 남겨 다른 인스턴스의 기동 복구가 정상 배치를 회수하지 않게 한다.
    @Modifying
    @Transactional
    @Query("""
            update BatchJobLog b
               set b.updatedAt = CURRENT_TIMESTAMP
             where b.id = :id
               and b.status = com.sangkwon.sangkwonplatform.global.batch.BatchStatus.RUNNING
            """)
    int touchRunningHeartbeat(@Param("id") Long id);

    // 마지막 하트비트가 임계보다 오래된 RUNNING만 조건부로 회수한다.
    // 조회 후 저장으로 나누지 않아 복구와 하트비트가 경합해도 최신 상태를 다시 확인한다.
    @Modifying
    @Transactional
    @Query("""
            update BatchJobLog b
               set b.status = com.sangkwon.sangkwonplatform.global.batch.BatchStatus.FAILED,
                   b.errorMsg = :errorMessage,
                   b.endedAt = CURRENT_TIMESTAMP,
                   b.updatedAt = CURRENT_TIMESTAMP
             where b.status = com.sangkwon.sangkwonplatform.global.batch.BatchStatus.RUNNING
               and b.updatedAt < :cutoff
            """)
    int failStaleRunningBefore(@Param("cutoff") LocalDateTime cutoff,
                               @Param("errorMessage") String errorMessage);

    // 관리자 초기화: 특정 데이터셋의 RUNNING만
    List<BatchJobLog> findByDatasetCdAndStatus(String datasetCd, BatchStatus status);

    // 보존 정리: 시작 시각이 기준보다 오래된 이력 삭제(운영 이력의 무한 증식 방지). 자체 트랜잭션.
    @Modifying
    @Transactional
    @Query("delete from BatchJobLog b where b.startedAt < :cutoff")
    int deleteByStartedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
