package com.sangkwon.sangkwonplatform.admin.account.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 관리자 로그인 실패를 접속 IP별 슬라이딩 윈도로 제한하는 인메모리 레이트 리미터.
// 계정 단위 잠금(5회)이 못 막는 password-spraying(여러 계정을 IP 하나로 돌아가며 시도)을 완화한다.
// 단일 인스턴스 전제(회원용 MemberLoginRateLimiter와 동일). 성공 시 해당 IP 카운터를 비운다.
@Component
public class AdminLoginRateLimiter {

    // IP는 여러 계정 시도를 합산하므로 계정 임계(5)보다 넉넉히 잡되, 스프레잉(수십~수백 시도)은 확실히 잡는다.
    private static final int MAX_FAILURES = 10;
    // 윈도는 계정 잠금 쿨다운(15분)보다 길게 둔다. 같게 두면 '5회 실패→잠금→15분 대기→해제→반복'의
    // 표적 공격에서 이전 사이클 실패가 창에서 만료돼 IP 임계에 영영 도달하지 못한다(여러 쿨다운 사이클을 합산해 차단).
    private static final Duration WINDOW = Duration.ofMinutes(30);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        if (ip == null) {
            return false;
        }
        Deque<Instant> q = failures.get(ip);
        if (q == null) {
            return false;
        }
        synchronized (q) {
            prune(q);
            return q.size() >= MAX_FAILURES;
        }
    }

    public void recordFailure(String ip) {
        if (ip == null) {
            return;
        }
        Deque<Instant> q = failures.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(Instant.now());
        }
    }

    public void reset(String ip) {
        if (ip != null) {
            failures.remove(ip);
        }
    }

    private void prune(Deque<Instant> q) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
            q.pollFirst();
        }
    }
}
