package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import jakarta.validation.constraints.NotNull;

public record AdminRoleUpdateRequest(
        @NotNull(message = "권한을 선택해 주세요!")
        AdminRole role
) {
}
