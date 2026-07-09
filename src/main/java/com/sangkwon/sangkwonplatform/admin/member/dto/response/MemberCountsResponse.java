package com.sangkwon.sangkwonplatform.admin.member.dto.response;

// 상태 필터 칩에 표시할 상태별 회원 수
public record MemberCountsResponse(
        long total,
        long active,
        long dormant,
        long banned,
        long withdrawn
) {
}
