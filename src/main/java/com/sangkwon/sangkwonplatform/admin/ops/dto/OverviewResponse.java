package com.sangkwon.sangkwonplatform.admin.ops.dto;

public record OverviewResponse(
        long memberCount,
        long todaySignups,
        long reportCount,
        long todayReports,
        long monthRevenue,
        long activeProCount
) {
}
