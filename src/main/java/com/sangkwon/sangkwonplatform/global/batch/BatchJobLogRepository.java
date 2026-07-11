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

    // 기동 시 좀비 정리: 남아 있는 RUNNING 전부
    List<BatchJobLog> findByStatus(BatchStatus status);

    // 관리자 초기화: 특정 데이터셋의 RUNNING만
    List<BatchJobLog> findByDatasetCdAndStatus(String datasetCd, BatchStatus status);

    // 보존 정리: 시작 시각이 기준보다 오래된 이력 삭제(운영 이력의 무한 증식 방지). 자체 트랜잭션.
    @Modifying
    @Transactional
    @Query("delete from BatchJobLog b where b.startedAt < :cutoff")
    int deleteByStartedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
