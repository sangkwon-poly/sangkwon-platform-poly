package com.sangkwon.sangkwonplatform.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchLogCreateRequest(

        @NotBlank
        @Size(max = 200)
        String keyword,

        @Size(max = 20)
        String trdarCd
) {
}
