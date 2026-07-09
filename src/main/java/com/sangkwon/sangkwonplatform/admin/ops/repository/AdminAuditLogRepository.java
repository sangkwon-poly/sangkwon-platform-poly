package com.sangkwon.sangkwonplatform.admin.ops.repository;

import com.sangkwon.sangkwonplatform.admin.ops.entity.AdminAuditLog;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findByOrderByCreatedAtDesc(Limit limit);

    // 감사 로그 조회: 행위(action) 필터(선택) + 페이징. 정렬은 Pageable로 넘긴다.
    @Query("select a from AdminAuditLog a where (:action is null or a.action = :action)")
    Page<AdminAuditLog> search(@Param("action") String action, Pageable pageable);
}
