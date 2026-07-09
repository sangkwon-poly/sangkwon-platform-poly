package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPasswordUpdateRequest(

        @NotBlank(message = "현재 비밀번호를 작성해 주세요!")
        String currentPassword,
        @NotBlank(message = "새 비밀번호를 작성해 주세요!")
        @Size(min = 10, max = 72, message = "비밀번호는 10자 이상 72자 이하로 설정해 주세요!")
        String newPassword
) {
}
