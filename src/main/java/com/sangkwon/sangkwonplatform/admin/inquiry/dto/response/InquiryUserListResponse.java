package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;

import java.time.LocalDateTime;

// 회원 본인 문의 목록 행. 본인 목록이라 회원 식별자는 노출하지 않는다.
public record InquiryUserListResponse(
        Long inquiryId,
        String title,
        InquiryStatus status,
        LocalDateTime createdAt,
        LocalDateTime answeredAt,
        boolean unreadAnswer
) {
    public static InquiryUserListResponse from(Inquiry inquiry) {
        return new InquiryUserListResponse(
                inquiry.getInquiryId(),
                inquiry.getTitle(),
                inquiry.getStatus(),
                inquiry.getCreatedAt(),
                inquiry.getAnsweredAt(),
                inquiry.isAnswerUnread()
        );
    }
}
