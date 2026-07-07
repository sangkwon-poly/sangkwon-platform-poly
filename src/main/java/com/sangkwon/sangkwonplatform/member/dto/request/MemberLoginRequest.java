package com.sangkwon.sangkwonplatform.member.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MemberLoginRequest(
        @NotBlank
        String loginId,

        @NotBlank
        String password,

        boolean remember
) {
}
