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

    // 감사 로그 조회: 행위(action)·행위자(adminId)·대상(targetType/targetId) 필터(모두 선택) + 페이징.
    // 계정 화면의 '이 관리자 활동'(adminId)과 '이 계정 변경 이력'(targetType=ADMIN,targetId) 드릴다운에 쓴다.
    @Query("""
            select a from AdminAuditLog a
            where (:action is null or a.action = :action)
              and (:adminId is null or a.adminId = :adminId)
              and (:targetType is null or a.targetType = :targetType)
              and (:targetId is null or a.targetId = :targetId)
            """)
    Page<AdminAuditLog> search(@Param("action") String action, @Param("adminId") Long adminId,
                               @Param("targetType") String targetType, @Param("targetId") String targetId,
                               Pageable pageable);
}
