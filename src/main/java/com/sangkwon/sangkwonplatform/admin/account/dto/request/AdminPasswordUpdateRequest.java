package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminPasswordUpdateRequest(

        @NotBlank(message = "현재 비밀번호를 작성해 주세요!")
        String currentPassword,
        @NotBlank(message = "새 비밀번호를 작성해 주세요!")
        String newPassword
) {
}
