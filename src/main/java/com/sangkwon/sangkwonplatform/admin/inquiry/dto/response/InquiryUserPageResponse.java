package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// 회원 본인 문의 목록 페이지 응답. 관리자용(InquiryPageResponse)과 노출 필드가 달라 따로 둔다.
public record InquiryUserPageResponse(
        List<InquiryUserListResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static InquiryUserPageResponse from(Page<InquiryUserListResponse> p) {
        return new InquiryUserPageResponse(
                p.getContent(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages());
    }
}
