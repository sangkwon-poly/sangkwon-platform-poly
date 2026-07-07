package com.sangkwon.sangkwonplatform.admin.adminUser.dto.request;

import com.sangkwon.sangkwonplatform.admin.adminUser.entity.enums.AdminStatus;

public record AdminStatusUpdateRequest(
        AdminStatus status
) {
}
