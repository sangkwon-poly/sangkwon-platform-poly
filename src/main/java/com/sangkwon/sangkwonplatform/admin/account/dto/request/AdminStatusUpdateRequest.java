package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;

public record AdminStatusUpdateRequest(
        AdminStatus status
) {
}
