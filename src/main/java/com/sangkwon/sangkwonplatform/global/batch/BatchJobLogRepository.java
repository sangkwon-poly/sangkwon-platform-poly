package com.sangkwon.sangkwonplatform.global.batch;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
