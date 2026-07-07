package com.sangkwon.sangkwonplatform.admin.adminUser.dto.response;

import com.sangkwon.sangkwonplatform.admin.adminUser.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.adminUser.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.adminUser.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.adminUser.entity.enums.HashAlgo;

import java.time.LocalDateTime;

public record AdminListResponse(
        Long adminId,
        String loginId,
        String adminName,
        AdminRole role,
        AdminStatus status,
        int failedLoginCnt,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminListResponse from (AdminUser adminUser){
        return new AdminListResponse(
                adminUser.getAdminId(),
                adminUser.getLoginId(),
                adminUser.getAdminName(),
                adminUser.getRole(),
                adminUser.getStatus(),
                adminUser.getFailedLoginCnt(),
                adminUser.getLastLoginAt(),
                adminUser.getCreatedAt(),
                adminUser.getUpdatedAt()
        );
    }
}
