package com.sangkwon.sangkwonplatform.admin.ops.dto;

import java.util.List;

// 감사 로그 페이지 응답. Page 직렬화 대신 프론트가 쓰는 필드만 고정해 노출한다.
public record AuditPageResponse(
        List<AuditLogResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
