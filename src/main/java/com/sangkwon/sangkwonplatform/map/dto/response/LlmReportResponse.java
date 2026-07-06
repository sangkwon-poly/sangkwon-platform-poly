package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.entity.LlmReport;

import java.time.LocalDateTime;

public record LlmReportResponse(
        String trdarCd,
        String stdrYyquCd,
        String resultText,
        String modelName,
        LocalDateTime createdAt
) {
    public static LlmReportResponse from(LlmReport r) {
        return new LlmReportResponse(
                r.getTrdarCd(),
                r.getStdrYyquCd(),
                r.getResultText(),
                r.getModelName(),
                r.getCreatedAt()
        );
    }
}
