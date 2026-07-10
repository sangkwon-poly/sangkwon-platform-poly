package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;

import java.time.LocalDateTime;

// 회원 본인 문의 상세(답변 포함). 소유 검증은 서비스에서 끝나므로 회원 식별자는 노출하지 않는다.
public record InquiryUserAnswerResponse(
        Long inquiryId,
        String title,
        String content,
        InquiryStatus status,
        String answer,
        String adminName,
        LocalDateTime answeredAt,
        LocalDateTime createdAt
) {
    public static InquiryUserAnswerResponse from(Inquiry inquiry) {
        AdminUser admin = inquiry.getAdmin();
        return new InquiryUserAnswerResponse(
                inquiry.getInquiryId(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getStatus(),
                inquiry.getAnswer(),
                admin == null ? null : admin.getAdminName(),
                inquiry.getAnsweredAt(),
                inquiry.getCreatedAt()
        );
    }
}
