package com.sangkwon.sangkwonplatform.admin.inquiry.dto.request;

import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryAnswerRequest(

        @NotBlank(message = "문의 답변을 작성해 주세요!")
        String answer
) {

}
