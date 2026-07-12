package com.sangkwon.sangkwonplatform.global.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

// 로그인·공개 쓰기 요청 빈도를 공유 DB(RATE_LIMIT_BUCKET)에 원자적으로 선점하는 레이트 리미터.
// 인프로세스 카운터는 인스턴스마다 따로라 다중 인스턴스에서 실효 임계가 N배 느슨해진다. DB로 빼 전역 합산한다.
@Component
public class DbRateLimiter {

    private final JdbcTemplate jt;

    public DbRateLimiter(JdbcTemplate jt) {
        this.jt = jt;
    }

    // 요청 처리 전에 슬롯을 원자적으로 증가시킨다. 검사 후 실패 기록을 따로 하던 방식과 달리,
    // 동시 요청마다 서로 다른 증가 결과를 받으므로 임계보다 많은 자격 검증이 통과하지 않는다.
    public boolean tryAcquire(String key, int maxHits, Duration window) {
        if (key == null) {
            return true;
        }
        LocalDateTime current = LocalDateTime.now();
        Timestamp now = Timestamp.valueOf(current);
        Timestamp cutoff = Timestamp.valueOf(current.minus(window));
        try {
            jt.update("""
                    MERGE INTO RATE_LIMIT_BUCKET b
                    USING (SELECT ? RATE_KEY, ? NOW_AT, ? CUTOFF_AT FROM dual) s
                       ON (b.RATE_KEY = s.RATE_KEY)
                    WHEN MATCHED THEN UPDATE SET
                        b.HIT_COUNT = CASE WHEN b.WINDOW_STARTED_AT < s.CUTOFF_AT
                            THEN 1 ELSE b.HIT_COUNT + 1 END,
                        b.WINDOW_STARTED_AT = CASE WHEN b.WINDOW_STARTED_AT < s.CUTOFF_AT
                            THEN s.NOW_AT ELSE b.WINDOW_STARTED_AT END,
                        b.UPDATED_AT = s.NOW_AT
                    WHEN NOT MATCHED THEN INSERT
                        (RATE_KEY, WINDOW_STARTED_AT, HIT_COUNT, UPDATED_AT)
                        VALUES (s.RATE_KEY, s.NOW_AT, 1, s.NOW_AT)
                    """, key, now, cutoff);
        } catch (DataIntegrityViolationException firstInsertRace) {
            // 같은 새 키의 최초 요청이 동시에 들어오면 둘 다 NOT MATCHED를 볼 수 있다.
            // PK 경쟁에서 진 요청은 기존 행을 갱신해 슬롯을 빠뜨리지 않는다.
            jt.update("""
                    UPDATE RATE_LIMIT_BUCKET
                       SET HIT_COUNT = CASE WHEN WINDOW_STARTED_AT < ? THEN 1 ELSE HIT_COUNT + 1 END,
                           WINDOW_STARTED_AT = CASE WHEN WINDOW_STARTED_AT < ? THEN ? ELSE WINDOW_STARTED_AT END,
                           UPDATED_AT = ?
                     WHERE RATE_KEY = ?
                    """, cutoff, cutoff, now, now, key);
        }
        Integer count = jt.queryForObject(
                "SELECT HIT_COUNT FROM RATE_LIMIT_BUCKET WHERE RATE_KEY = ?",
                Integer.class, key);
        return count != null && count <= maxHits;
    }

    // 오래된 버킷과 이전 버전이 쓰던 히트 행을 함께 정리한다.
    public int purgeOlderThan(Duration retention) {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minus(retention));
        int buckets = jt.update("DELETE FROM RATE_LIMIT_BUCKET WHERE UPDATED_AT < ?", cutoff);
        int legacyHits = jt.update("DELETE FROM LOGIN_RATE_HIT WHERE HIT_AT < ?", cutoff);
        return buckets + legacyHits;
    }
}
