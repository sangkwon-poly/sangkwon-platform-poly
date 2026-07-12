package com.sangkwon.sangkwonplatform.admin.account.service;

import com.sangkwon.sangkwonplatform.global.security.DbRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 관리자 로그인 실패를 접속 IP별로 제한한다. 공유 DB(DbRateLimiter)로 집계해 다중 인스턴스에서도 임계가 유지된다.
// 계정 단위 잠금(5회)이 못 막는 password-spraying(여러 계정을 IP 하나로 돌아가며 시도)을 완화한다.
@Component
public class AdminLoginRateLimiter {

    // IP는 여러 계정 시도를 합산하므로 계정 임계(5)보다 넉넉히 잡되, 스프레잉(수십~수백 시도)은 확실히 잡는다.
    private static final int MAX_FAILURES = 10;
    // 윈도는 계정 잠금 쿨다운(15분)보다 길게 둔다. 같게 두면 잠금·해제 반복 표적 공격에서 이전 사이클 실패가
    // 창에서 만료돼 IP 임계에 영영 도달하지 못한다(여러 쿨다운 사이클을 합산해 차단).
    private static final Duration WINDOW = Duration.ofMinutes(30);

    private final DbRateLimiter db;

    public AdminLoginRateLimiter(DbRateLimiter db) {
        this.db = db;
    }

    public boolean isBlocked(String ip) {
        return db.isBlocked(key(ip), MAX_FAILURES, WINDOW);
    }

    public void recordFailure(String ip) {
        if (ip != null) {
            db.record(key(ip));
        }
    }

    public void reset(String ip) {
        if (ip != null) {
            db.reset(key(ip));
        }
    }

    private static String key(String ip) {
        return "admin-login:" + ip;
    }
}
