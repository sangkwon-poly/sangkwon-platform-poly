package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import jakarta.validation.constraints.NotNull;

public record AdminStatusUpdateRequest(
        @NotNull(message = "상태를 선택해 주세요!")
        AdminStatus status
) {
}
