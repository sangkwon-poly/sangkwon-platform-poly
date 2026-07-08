package com.sangkwon.sangkwonplatform.member.dto.response;

import java.time.LocalDateTime;

import com.sangkwon.sangkwonplatform.member.entity.SearchLog;

public record SearchLogResponse(
        Long searchId,
        String keyword,
        String trdarCd,
        LocalDateTime searchedAt
) {
    public static SearchLogResponse from(SearchLog s) {
        return new SearchLogResponse(
                s.getSearchId(),
                s.getKeyword(),
                s.getTrdarCd(),
                s.getSearchedAt()
        );
    }
}
