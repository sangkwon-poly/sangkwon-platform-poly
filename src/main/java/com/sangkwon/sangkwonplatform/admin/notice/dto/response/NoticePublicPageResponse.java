package com.sangkwon.sangkwonplatform.admin.notice.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// 공개 공지 목록 페이지 응답. 관리자용(NoticePageResponse)과 노출 필드가 달라 따로 둔다.
public record NoticePublicPageResponse(
        List<NoticeSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static NoticePublicPageResponse from(Page<NoticeSummaryResponse> p) {
        return new NoticePublicPageResponse(
                p.getContent(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages());
    }
}
