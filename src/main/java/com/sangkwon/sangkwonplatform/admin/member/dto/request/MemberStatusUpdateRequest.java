package com.sangkwon.sangkwonplatform.admin.member.dto.request;

import com.sangkwon.sangkwonplatform.member.entity.MemberStatus;
import jakarta.validation.constraints.NotNull;

public record MemberStatusUpdateRequest(
        @NotNull(message = "변경할 상태를 선택해 주세요!")
        MemberStatus status
) {
}
