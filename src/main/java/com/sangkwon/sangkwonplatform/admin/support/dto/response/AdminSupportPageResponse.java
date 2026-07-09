package com.sangkwon.sangkwonplatform.admin.support.dto.response;

import java.util.List;

public record AdminSupportPageResponse(
        List<AdminSupportCardResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
