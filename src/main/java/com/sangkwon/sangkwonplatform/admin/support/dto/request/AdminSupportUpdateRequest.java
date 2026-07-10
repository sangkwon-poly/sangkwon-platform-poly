package com.sangkwon.sangkwonplatform.admin.support.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

// 관리자 콘텐츠 수정. 원본 식별자와 유형은 두고 노출용 필드만 받는다.
public record AdminSupportUpdateRequest(
        @NotBlank(message = "제목은 필수입니다!")
        String title,
        String region,
        String target,
        String description,
        String contact,
        // 원문 링크는 http/https만 허용한다(javascript: 등 스킴이 저장돼 화면 href로 나가는 것 방지)
        @Pattern(regexp = "^(https?://.+)?$", message = "원문 URL은 http 또는 https로 시작해야 합니다")
        String detailUrl,
        LocalDate applyBgngDe,
        LocalDate applyEndDe,
        String applyPeriodRaw
) {
}
