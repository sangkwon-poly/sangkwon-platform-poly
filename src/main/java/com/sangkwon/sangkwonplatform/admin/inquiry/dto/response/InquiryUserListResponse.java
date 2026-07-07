package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;

import java.time.LocalDateTime;

public record InquiryUserListResponse(
        Long inquiryId,
        Long MemberId,
        String title,
        LocalDateTime answeredAt
) {
    public static InquiryUserListResponse from (Inquiry inquiry){
        return new InquiryUserListResponse(
                inquiry.getInquiryId(),
                null,//추후에 memberId 작성,
                inquiry.getTitle(),
                inquiry.getAnsweredAt()
        );
    }
}
