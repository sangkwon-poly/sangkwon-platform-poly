package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;
import com.sangkwon.sangkwonplatform.member.entity.Member;

import java.time.LocalDateTime;

public record InquiryAdminSummaryResponse(
        Long inquiryId,
        Long memberId,
        String memberName,
        String title,
        InquiryStatus status,
        Long adminId,
        String adminName,
        LocalDateTime answeredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {
    public static InquiryAdminSummaryResponse from (Inquiry inquiry){
        AdminUser admin = inquiry.getAdmin();
        Member member = inquiry.getMember();
        return new InquiryAdminSummaryResponse(
                inquiry.getInquiryId(),
            member == null ? null : member.getMemberId(),
            member == null ? null : member.getNickname(),
            inquiry.getTitle(),
            inquiry.getStatus(),
            admin == null ? null : admin.getAdminId(),
            admin == null ? null : admin.getAdminName(),
            inquiry.getAnsweredAt(),
            inquiry.getCreatedAt(),
            inquiry.getUpdatedAt()
        );
    }
}
