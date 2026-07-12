package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.global.security.DbRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 검색 로그 쓰기 남용을 IP·회원별로 제한한다. 익명 POST가 테이블을 키우는 것을 막는다.
@Component
public class SearchLogRateLimiter {

    private static final int ANON_MAX = 60;
    private static final int MEMBER_MAX = 120;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final DbRateLimiter db;

    public SearchLogRateLimiter(DbRateLimiter db) {
        this.db = db;
    }

    public boolean tryAcquire(Long memberId, String clientIp) {
        String key = memberId != null
                ? "search-log:member:" + memberId
                : "search-log:ip:" + (clientIp == null ? "unknown" : clientIp);
        int max = memberId != null ? MEMBER_MAX : ANON_MAX;
        return db.tryAcquire(key, max, WINDOW);
    }
}
