package com.sangkwon.sangkwonplatform.admin.ops.repository;

import com.sangkwon.sangkwonplatform.admin.ops.entity.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {

    List<ApiUsageLog> findByUsageDateOrderByApiName(LocalDate usageDate);
}
