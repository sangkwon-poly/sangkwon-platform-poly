package com.sangkwon.sangkwonplatform.admin.adminUser.dto.request;

import com.sangkwon.sangkwonplatform.admin.adminUser.entity.enums.AdminRole;

public record AdminRoleUpdateRequest(
        AdminRole role
) {
}
