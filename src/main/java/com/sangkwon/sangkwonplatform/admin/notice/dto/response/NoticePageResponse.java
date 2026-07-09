package com.sangkwon.sangkwonplatform.admin.notice.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// 공지 목록 페이지 응답. Page 직렬화 대신 프론트가 쓰는 필드만 고정해 노출한다.
public record NoticePageResponse(
        List<NoticeAdminListResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static NoticePageResponse from(Page<NoticeAdminListResponse> p) {
        return new NoticePageResponse(
                p.getContent(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages());
    }
}
