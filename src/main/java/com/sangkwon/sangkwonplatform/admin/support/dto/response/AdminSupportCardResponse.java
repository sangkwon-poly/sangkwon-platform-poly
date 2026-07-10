package com.sangkwon.sangkwonplatform.admin.support.dto.response;

import java.time.LocalDate;

// 관리자 목록 한 행. 공개 카드와 달리 노출여부(visible)를 포함한다.
public record AdminSupportCardResponse(
        String sourceCd,
        String programId,
        String title,
        String typeLabel,
        String region,
        LocalDate applyBgngDe,
        LocalDate applyEndDe,
        String applyPeriodRaw,
        String status,
        Integer dday,
        boolean visible,
        String detailUrl
) {
}
