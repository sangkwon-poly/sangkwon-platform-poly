package com.sangkwon.sangkwonplatform.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberSignupRequest(
        @NotBlank
        @Size(min = 4, max = 50)
        String loginId,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 50)
        String nickname,

        @NotBlank
        @Size(min = 8, max = 72) // BCrypt는 72바이트까지만 처리하므로 상한을 맞춘다
        String password
) {
}
