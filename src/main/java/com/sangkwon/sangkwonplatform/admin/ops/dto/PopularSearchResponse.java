package com.sangkwon.sangkwonplatform.admin.ops.dto;

import com.sangkwon.sangkwonplatform.member.repository.SearchLogRepository;

// 관리자 대시보드 인기 검색어 카드용. 검색어와 기간 내 검색 횟수.
public record PopularSearchResponse(String keyword, long count) {

    public static PopularSearchResponse from(SearchLogRepository.PopularKeyword row) {
        return new PopularSearchResponse(row.getKeyword(), row.getCnt());
    }
}
