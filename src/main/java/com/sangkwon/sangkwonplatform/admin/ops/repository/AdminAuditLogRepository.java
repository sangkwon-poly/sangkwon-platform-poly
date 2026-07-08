package com.sangkwon.sangkwonplatform.admin.ops.repository;

import com.sangkwon.sangkwonplatform.admin.ops.entity.AdminAuditLog;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findByOrderByCreatedAtDesc(Limit limit);
}
