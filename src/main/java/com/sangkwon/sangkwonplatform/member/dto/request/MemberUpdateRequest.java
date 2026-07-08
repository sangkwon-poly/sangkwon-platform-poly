package com.sangkwon.sangkwonplatform.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record MemberUpdateRequest(
        @Size(max = 50)
        String nickname,

        @Email
        @Size(max = 255)
        String email
) {
}
