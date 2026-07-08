package com.sangkwon.sangkwonplatform.admin.ops.dto;

import com.sangkwon.sangkwonplatform.admin.ops.entity.ApiUsageLog;

import java.time.LocalDate;

public record ApiUsageResponse(
        String apiName,
        LocalDate usageDate,
        long callCnt,
        long dailyLimit,
        int usagePct
) {
    public static ApiUsageResponse from(ApiUsageLog a) {
        int pct = a.getDailyLimit() > 0
                ? (int) Math.min(100, Math.round(a.getCallCnt() * 100.0 / a.getDailyLimit()))
                : 0;
        return new ApiUsageResponse(a.getApiName(), a.getUsageDate(), a.getCallCnt(), a.getDailyLimit(), pct);
    }
}
