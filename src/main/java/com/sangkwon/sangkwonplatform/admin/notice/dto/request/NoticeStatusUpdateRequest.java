package com.sangkwon.sangkwonplatform.admin.notice.dto.request;

import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.NoticeStatus;
import jakarta.validation.constraints.NotNull;

public record NoticeStatusUpdateRequest(
        @NotNull(message = "공지 상태를 선택해 주세요!")
        NoticeStatus status
) {
}
