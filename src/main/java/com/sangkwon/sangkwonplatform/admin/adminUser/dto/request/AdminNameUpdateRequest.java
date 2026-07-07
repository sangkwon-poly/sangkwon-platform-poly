package com.sangkwon.sangkwonplatform.admin.adminUser.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminNameUpdateRequest(

        @NotBlank(message = "관리자 이름은 필수입니다!")
        @Size(max = 50, message = "관리자 이름은 50자 이하로 설정해 주세요!")
        String adminName
) {
}
