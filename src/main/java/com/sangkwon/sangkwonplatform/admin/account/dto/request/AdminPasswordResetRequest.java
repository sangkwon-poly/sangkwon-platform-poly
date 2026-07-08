package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 최고관리자가 다른 관리자의 비밀번호를 재설정할 때 쓰는 요청(현재 비밀번호 불필요).
public record AdminPasswordResetRequest(

        @NotBlank(message = "새 비밀번호를 입력해 주세요!")
        @Size(min = 4, message = "비밀번호는 4자 이상으로 설정해 주세요!")
        String newPassword
) {
}
