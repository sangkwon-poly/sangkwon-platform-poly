package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;

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
        return new InquiryAdminDetailResponse(
                inquiry.getInquiryId(),
                null,
                null,
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getAnswer(),
                inquiry.getAdmin().getAdminName(),
                inquiry.getAnsweredAt(),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt()
        );
    }
}
