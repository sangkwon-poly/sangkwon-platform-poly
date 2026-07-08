package com.sangkwon.sangkwonplatform.admin.inquiry.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryUpdateRequest(
        @NotBlank(message = "문의 제목은 필수입니다!")
        @Size(max = 200, message = "문의 제목은 200자 이하로 입력해 주세요!")
        String title,

        @NotBlank(message = "문의 내용은 필수입니다!")
        String content
) {
}
