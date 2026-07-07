package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;

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
        return new InquiryAdminSummaryResponse(
                inquiry.getInquiryId(),
            null,
            null,
            inquiry.getTitle(),
            inquiry.getStatus(),
            inquiry.getAdmin().getAdminId(),
            inquiry.getAdmin().getAdminName(),
            inquiry.getAnsweredAt(),
            inquiry.getCreatedAt(),
            inquiry.getUpdatedAt()
        );
    }
}
