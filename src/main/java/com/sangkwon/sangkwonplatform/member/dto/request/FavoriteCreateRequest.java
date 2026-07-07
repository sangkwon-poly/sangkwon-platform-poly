package com.sangkwon.sangkwonplatform.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FavoriteCreateRequest(
        @NotBlank
        @Size(max = 20)
        String trdarCd
) {
}
