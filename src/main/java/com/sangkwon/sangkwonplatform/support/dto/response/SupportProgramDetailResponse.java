package com.sangkwon.sangkwonplatform.support.dto.response;

import java.time.LocalDate;
import java.util.List;

// 상세. kstartup은 K-Startup 공고일 때만 채우고, 기업마당이면 null(화면에서 전용 섹션 숨김).
public record SupportProgramDetailResponse(
        String sourceCd,
        String programId,
        String title,
        String typeTab,
        String typeLabel,
        String region,
        String target,
        String description,
        LocalDate applyBgngDe,
        LocalDate applyEndDe,
        String applyPeriodRaw,
        String status,
        Integer dday,
        String contact,
        String detailUrl,
        boolean visible,
        Kstartup kstartup
) {
    public record Kstartup(
            String foundingPeriod,
            String targetAge,
            List<String> applyMethods,
            String exclusion,
            String preference,
            String supervisor,
            String operator,
            String guideUrl
    ) {
    }
}
