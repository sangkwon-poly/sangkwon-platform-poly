package com.sangkwon.sangkwonplatform.admin.member.dto.response;

import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.MemberStatus;
import com.sangkwon.sangkwonplatform.member.entity.Role;

import java.time.LocalDateTime;

public record AdminMemberResponse(
        Long memberId,
        String loginId,
        String nickname,
        String email,
        Role role,
        MemberStatus status,
        boolean pro,
        LocalDateTime planUntil,
        LocalDateTime lastLoginAt,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt
) {
    public static AdminMemberResponse from(Member m) {
        return new AdminMemberResponse(
                m.getMemberId(),
                m.getLoginId(),
                m.getNickname(),
                m.getEmail(),
                m.getRole(),
                m.getStatus(),
                m.isPro(),
                m.getPlanUntil(),
                m.getLastLoginAt(),
                m.getWithdrawnAt(),
                m.getCreatedAt());
    }
}
