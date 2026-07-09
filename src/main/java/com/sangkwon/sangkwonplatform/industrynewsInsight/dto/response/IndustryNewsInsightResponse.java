package com.sangkwon.sangkwonplatform.industrynewsInsight.dto.response;

import com.sangkwon.sangkwonplatform.industrynewsInsight.entity.IndustryNewsInsight;

public record IndustryNewsInsightResponse(
        String indutyCd,
        String indutyNm,
        String yearMonth,
        String insightText,
        Integer basedOnCount
){
    public static IndustryNewsInsightResponse from(IndustryNewsInsight entity){
        return new IndustryNewsInsightResponse(
                entity.getIndutyCd(),
                entity.getIndutyNm(),
                entity.getYearMonth(),
                entity.getInsightText(),
                entity.getBasedOnCount()
        );
    }
}
