package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.member.entity.Member;

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
        AdminUser admin = inquiry.getAdmin();
        Member member = inquiry.getMember();
        return new InquiryUserAnswerResponse(
                inquiry.getInquiryId(),
                inquiry.getTitle(),
                inquiry.getContent(),
                member == null ? null : member.getMemberId(),
                inquiry.getAnswer(),
                admin == null ? null : admin.getAdminName(),
                inquiry.getAnsweredAt()
        );
    }
}
