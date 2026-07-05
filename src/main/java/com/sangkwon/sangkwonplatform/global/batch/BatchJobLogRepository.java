package com.sangkwon.sangkwonplatform.global.batch;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchJobLogRepository extends JpaRepository<BatchJobLog, Long> {
}
