package com.sangkwon.sangkwonplatform.support.dto.response;

import java.time.LocalDate;

// 목록 카드 한 건. status/dday/typeLabel은 서비스에서 계산해 담는다.
public record SupportProgramCardResponse(
        String sourceCd,
        String programId,
        String title,
        String typeTab,
        String typeLabel,
        String org,
        String region,
        LocalDate applyBgngDe,
        LocalDate applyEndDe,
        String applyPeriodRaw,
        String status,
        Integer dday,
        String detailUrl
) {
}
