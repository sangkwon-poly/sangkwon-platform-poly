package com.sangkwon.sangkwonplatform.industrytrademark.dto.response;

import com.sangkwon.sangkwonplatform.industrytrademark.entity.IndustryTrademark;

import java.time.LocalDate;

public record IndustryTrademarkResponse(
        String applNo,
        String title,
        String applicantNm,
        LocalDate applDate,
        String status
) {
    public static IndustryTrademarkResponse from(IndustryTrademark e) {
        return new IndustryTrademarkResponse(
                e.getApplNo(),
                e.getTitle(),
                e.getApplicantNm(),
                e.getApplDate(),
                e.getStatus()
        );
    }
}
