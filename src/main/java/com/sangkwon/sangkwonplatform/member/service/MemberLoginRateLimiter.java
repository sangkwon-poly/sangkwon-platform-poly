package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.global.security.DbRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 회원 로그인 요청을 IP별로 제한한다. 공유 DB(DbRateLimiter)에서 슬롯을 선점해 동시 요청도 임계를 지킨다.
// 공개 로그인 엔드포인트의 무차별 대입을 완화한다.
// 로그인 성공으로는 카운터를 비우지 않는다: 자기 계정 성공으로 카운터를 지우는 스프레잉 우회를 막는다.
@Component
public class MemberLoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final DbRateLimiter db;

    public MemberLoginRateLimiter(DbRateLimiter db) {
        this.db = db;
    }

    public boolean tryAcquire(String key) {
        return db.tryAcquire(scoped(key), MAX_FAILURES, WINDOW);
    }

    private static String scoped(String key) {
        return "member-login:" + key;
    }
}
