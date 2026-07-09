package com.sangkwon.sangkwonplatform.admin.support.dto.response;

import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository.AdminSupportCounts;

public record AdminSupportCountsResponse(
        long total,
        long visible,
        long hidden,
        long bizinfo,
        long kstartup
) {
    public static AdminSupportCountsResponse from(AdminSupportCounts c) {
        return new AdminSupportCountsResponse(c.getTotal(), c.getVisible(), c.getHidden(), c.getBizinfo(), c.getKstartup());
    }
}
