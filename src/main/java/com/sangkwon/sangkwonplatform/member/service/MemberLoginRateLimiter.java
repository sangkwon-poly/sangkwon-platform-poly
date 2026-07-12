package com.sangkwon.sangkwonplatform.member.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// IP별 로그인 실패를 슬라이딩 윈도로 제한하는 인메모리 레이트 리미터.
// 공개 로그인 엔드포인트의 무차별 대입을 완화한다(단일 인스턴스 기준).
// 로그인 성공으로는 카운터를 비우지 않는다: 자기 계정 성공으로 카운터를 지우는 스프레잉 우회를 막는다.
// 실패는 윈도(10분)가 지나면 자연 만료된다. reset은 명시적 초기화용이며 성공 경로에서 호출하지 않는다.
@Component
public class MemberLoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Deque<Instant> q = failures.get(key);
        if (q == null) {
            return false;
        }
        synchronized (q) {
            prune(q);
            return q.size() >= MAX_FAILURES;
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(Instant.now());
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> q) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
            q.pollFirst();
        }
    }
}
