package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;

public record AdminRoleUpdateRequest(
        AdminRole role
) {
}
