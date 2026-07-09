package com.sangkwon.sangkwonplatform.admin.support.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

// 관리자 콘텐츠 수정. 원본 식별자와 유형은 두고 노출용 필드만 받는다.
public record AdminSupportUpdateRequest(
        @NotBlank
        String title,
        String region,
        String target,
        String description,
        String contact,
        String detailUrl,
        LocalDate applyBgngDe,
        LocalDate applyEndDe,
        String applyPeriodRaw
) {
}
