package com.sangkwon.sangkwonplatform.global.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

// 로그인 실패·요청 빈도를 공유 DB(LOGIN_RATE_HIT)에 적재하는 레이트 리미터.
// 인프로세스 카운터는 인스턴스마다 따로라 다중 인스턴스에서 실효 임계가 N배 느슨해진다. DB로 빼 전역 합산한다.
// record는 호출한 로그인 트랜잭션에 같은 커넥션으로 참여한다(별도 커넥션을 잡지 않아 풀을 아낀다).
// 로그인은 자격 실패 예외에 noRollbackFor를 걸어 이 기록이 롤백되지 않고 커밋되게 한다(로그인 서비스 참고).
@Component
public class DbRateLimiter {

    private final JdbcTemplate jt;

    public DbRateLimiter(JdbcTemplate jt) {
        this.jt = jt;
    }

    public void record(String key) {
        if (key == null) {
            return;
        }
        jt.update("INSERT INTO LOGIN_RATE_HIT (RATE_KEY, HIT_AT) VALUES (?, ?)",
                key, Timestamp.valueOf(LocalDateTime.now()));
    }

    public boolean isBlocked(String key, int maxHits, Duration window) {
        if (key == null) {
            return false;
        }
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minus(window));
        Long cnt = jt.queryForObject(
                "SELECT COUNT(*) FROM LOGIN_RATE_HIT WHERE RATE_KEY = ? AND HIT_AT >= ?",
                Long.class, key, cutoff);
        return cnt != null && cnt >= maxHits;
    }

    public void reset(String key) {
        if (key != null) {
            jt.update("DELETE FROM LOGIN_RATE_HIT WHERE RATE_KEY = ?", key);
        }
    }

    // 오래된 히트 정리(스케줄러에서 호출). 가장 긴 윈도보다 넉넉히 남기고 지운다.
    public int purgeOlderThan(Duration retention) {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minus(retention));
        return jt.update("DELETE FROM LOGIN_RATE_HIT WHERE HIT_AT < ?", cutoff);
    }
}
