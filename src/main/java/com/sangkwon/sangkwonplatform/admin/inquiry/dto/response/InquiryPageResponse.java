package com.sangkwon.sangkwonplatform.admin.inquiry.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// 문의 목록 페이지 응답. Page 직렬화 대신 프론트가 쓰는 필드만 고정해 노출한다.
public record InquiryPageResponse(
        List<InquiryAdminSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static InquiryPageResponse from(Page<InquiryAdminSummaryResponse> p) {
        return new InquiryPageResponse(
                p.getContent(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages());
    }
}
