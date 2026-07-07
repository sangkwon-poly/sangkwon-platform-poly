package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;

import java.time.LocalDateTime;

public record InquiryUserAnswerResponse(
        Long inquiryId,
        String title,
        String content,
        Long memberId,
        String answer,
        String adminName,
        LocalDateTime answeredAt
) {
    public static InquiryUserAnswerResponse from (Inquiry inquiry){
        return new InquiryUserAnswerResponse(
                inquiry.getInquiryId(),
                inquiry.getTitle(),
                inquiry.getContent(),
                null, //member 생기면 그 때 수정
                inquiry.getAnswer(),
                inquiry.getAdmin().getAdminName(),
                inquiry.getAnsweredAt()
        );
    }
}
