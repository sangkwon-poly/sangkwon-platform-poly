package com.sangkwon.sangkwonplatform.support.dto.response;

import java.util.List;

// 목록 응답. Page 직렬화 대신 프론트가 쓰는 필드만 고정한다.
// typeCounts는 유형 탭 배지 숫자, excludedByDetailFilter는 상세필터로 제외된 기업마당 건수(배너용).
public record SupportProgramPageResponse(
        List<SupportProgramCardResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<TypeCount> typeCounts,
        int excludedByDetailFilter
) {
    public record TypeCount(String tab, String label, long count) {
    }
}
