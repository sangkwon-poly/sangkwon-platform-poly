package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import com.sangkwon.sangkwonplatform.global.validation.BcryptPassword;
import jakarta.validation.constraints.NotBlank;

public record AdminPasswordUpdateRequest(

        @NotBlank(message = "현재 비밀번호를 작성해 주세요!")
        String currentPassword,
        @NotBlank(message = "새 비밀번호를 작성해 주세요!")
        @BcryptPassword(min = 10, message = "비밀번호는 10자 이상, UTF-8 기준 72바이트 이하여야 합니다.")
        String newPassword
) {
}
