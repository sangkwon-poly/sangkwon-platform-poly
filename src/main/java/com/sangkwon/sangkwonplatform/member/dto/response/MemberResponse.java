package com.sangkwon.sangkwonplatform.member.dto.response;

import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.MemberStatus;
import com.sangkwon.sangkwonplatform.member.entity.Role;

import java.time.LocalDateTime;

public record MemberResponse(
        Long memberId,
        String loginId,
        String email,
        String nickname,
        Role role,
        MemberStatus status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
    public static MemberResponse from(Member m) {
        return new MemberResponse(
                m.getMemberId(),
                m.getLoginId(),
                m.getEmail(),
                m.getNickname(),
                m.getRole(),
                m.getStatus(),
                m.getLastLoginAt(),
                m.getCreatedAt()
        );
    }
}
