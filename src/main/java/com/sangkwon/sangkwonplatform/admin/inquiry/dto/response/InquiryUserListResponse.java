package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.member.entity.Member;

import java.time.LocalDateTime;

public record InquiryUserListResponse(
        Long inquiryId,
        Long MemberId,
        String title,
        LocalDateTime answeredAt
) {
    public static InquiryUserListResponse from (Inquiry inquiry){
        Member member = inquiry.getMember();
        return new InquiryUserListResponse(
                inquiry.getInquiryId(),
                member == null ? null : member.getMemberId(),
                inquiry.getTitle(),
                inquiry.getAnsweredAt()
        );
    }
}
