package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.member.entity.Member;

import java.time.LocalDateTime;

public record InquiryAdminDetailResponse(
        Long inquiryId,
        Long memberId,
        String memberName,
        String title,
        String content,
        String answer,
        String adminName,
        LocalDateTime answeredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static InquiryAdminDetailResponse from (Inquiry inquiry){
        AdminUser admin = inquiry.getAdmin();
        Member member = inquiry.getMember();
        return new InquiryAdminDetailResponse(
                inquiry.getInquiryId(),
                member == null ? null : member.getMemberId(),
                member == null ? null : member.getNickname(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getAnswer(),
                admin == null ? null : admin.getAdminName(),
                inquiry.getAnsweredAt(),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt()
        );
    }
}
