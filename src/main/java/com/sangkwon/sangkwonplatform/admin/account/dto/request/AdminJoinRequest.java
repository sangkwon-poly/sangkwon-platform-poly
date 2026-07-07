package com.sangkwon.sangkwonplatform.admin.account.dto.request;

import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record AdminJoinRequest(
        @NotBlank(message = "로그인 ID는 필수입니다!")
        @Size(max = 50, message = "로그인 ID는 50자 이하로 설정해 주세요!")
        String loginId,
        @NotBlank(message = "비밀번호는 필수입니다!")
        String password,
        @NotBlank(message = "관리자 이름은 필수입니다!")
        String adminName,
        AdminRole role
) {
}
