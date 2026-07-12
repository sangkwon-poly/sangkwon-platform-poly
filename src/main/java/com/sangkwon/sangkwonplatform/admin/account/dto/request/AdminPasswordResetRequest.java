package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import com.sangkwon.sangkwonplatform.global.validation.BcryptPassword;
import jakarta.validation.constraints.NotBlank;

// 최고관리자가 다른 관리자의 비밀번호를 재설정할 때 쓰는 요청(현재 비밀번호 불필요).
public record AdminPasswordResetRequest(

        @NotBlank(message = "새 비밀번호를 입력해 주세요!")
        @BcryptPassword(min = 10, message = "비밀번호는 10자 이상, UTF-8 기준 72바이트 이하여야 합니다.")
        String newPassword
) {
}
