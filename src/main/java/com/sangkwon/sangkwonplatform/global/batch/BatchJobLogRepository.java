package com.sangkwon.sangkwonplatform.global.batch;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchJobLogRepository extends JpaRepository<BatchJobLog, Long> {

    List<BatchJobLog> findByOrderByStartedAtDesc(Limit limit);
}
