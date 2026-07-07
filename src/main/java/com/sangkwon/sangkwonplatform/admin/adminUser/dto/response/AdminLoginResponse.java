package com.sangkwon.sangkwonplatform.admin.adminUser.dto.response;

import com.sangkwon.sangkwonplatform.admin.adminUser.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.adminUser.entity.enums.AdminRole;

public record AdminLoginResponse(
        Long adminId,
        String loginId,
        String adminName,
        AdminRole role
) {
    public static AdminLoginResponse from (AdminUser adminUser){
        return new AdminLoginResponse(
                adminUser.getAdminId(),
                adminUser.getLoginId(),
                adminUser.getAdminName(),
                adminUser.getRole()
        );
    }
}
