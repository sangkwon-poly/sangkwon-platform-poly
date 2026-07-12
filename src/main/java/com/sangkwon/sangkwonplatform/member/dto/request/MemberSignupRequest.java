package com.sangkwon.sangkwonplatform.member.dto.request;

import com.sangkwon.sangkwonplatform.global.validation.BcryptPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberSignupRequest(
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9]{4,50}$", message = "아이디는 영문·숫자 4~50자여야 합니다.")
        String loginId,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Pattern(regexp = "^[가-힣a-zA-Z0-9_-]{2,20}$", message = "닉네임은 한글·영문·숫자 2~20자여야 합니다.")
        String nickname,

        @NotBlank
        @BcryptPassword(min = 8, message = "비밀번호는 8자 이상, UTF-8 기준 72바이트 이하여야 합니다.")
        String password
) {
}
