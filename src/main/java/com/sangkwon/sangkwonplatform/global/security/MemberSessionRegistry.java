package com.sangkwon.sangkwonplatform.global.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Map;

// 회원 세션 강제 만료. 세션 영속화(Spring Session JDBC)로 회원 세션이 principal(memberId)로 인덱싱되므로,
// 정지/휴면/탈퇴 시 해당 회원의 모든 활성 세션을 찾아 삭제해 즉시 로그아웃시킨다.
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSessionRegistry {

    // 회원 로그인 시 SecurityContext의 principal이 memberId라 세션의 PRINCIPAL_NAME 인덱스가 memberId 문자열이 된다.
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    // 회원의 모든 세션을 만료시킨다. 세션 저장소 오류가 상태 변경 자체를 막지 않도록 베스트에포트로 처리한다.
    public int revokeAll(Long memberId) {
        if (memberId == null) {
            return 0;
        }
        try {
            Map<String, ? extends Session> sessions =
                    sessionRepository.findByPrincipalName(String.valueOf(memberId));
            sessions.keySet().forEach(sessionRepository::deleteById);
            return sessions.size();
        } catch (Exception e) {
            log.warn("회원 {} 세션 만료 실패(상태 변경은 유지): {}", memberId, e.getMessage());
            return 0;
        }
    }
}
