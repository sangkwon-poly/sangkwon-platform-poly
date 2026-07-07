package com.sangkwon.sangkwonplatform.admin.adminUser.dto.session;

import com.sangkwon.sangkwonplatform.admin.adminUser.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.adminUser.entity.enums.AdminRole;

import java.io.Serializable;

public record AdminSession(
        Long adminId,
        String loginId,
        String adminName,
        AdminRole role
) implements Serializable {
    public static AdminSession from (AdminUser adminUser) {
        return new AdminSession(
                adminUser.getAdminId(),
                adminUser.getLoginId(),
                adminUser.getAdminName(),
                adminUser.getRole()
        );
    }
}
