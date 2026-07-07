package com.sangkwon.sangkwonplatform.admin.ops.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long adminId,
        String adminLoginId,
        String action,
        String targetType,
        String targetId,
        String detail,
        String ipAddr,
        LocalDateTime createdAt
) {
}
