package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.global.security.DbRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 회원 로그인 실패를 IP별로 제한한다. 공유 DB(DbRateLimiter)로 집계해 다중 인스턴스에서도 임계가 유지된다.
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

    public boolean isBlocked(String key) {
        return db.isBlocked(scoped(key), MAX_FAILURES, WINDOW);
    }

    public void recordFailure(String key) {
        if (key != null) {
            db.record(scoped(key));
        }
    }

    public void reset(String key) {
        if (key != null) {
            db.reset(scoped(key));
        }
    }

    private static String scoped(String key) {
        return "member-login:" + key;
    }
}
