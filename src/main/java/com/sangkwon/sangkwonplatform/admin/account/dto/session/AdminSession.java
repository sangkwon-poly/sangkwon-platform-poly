package com.sangkwon.sangkwonplatform.admin.account.dto.session;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;

import java.io.Serializable;

public record AdminSession(
        Long adminId,
        String loginId,
        String adminName,
        AdminRole role,
        int pwVersion
) implements Serializable {
    public static AdminSession from (AdminUser adminUser) {
        return new AdminSession(
                adminUser.getAdminId(),
                adminUser.getLoginId(),
                adminUser.getAdminName(),
                adminUser.getRole(),
                adminUser.getPwVersion()
        );
    }
}
