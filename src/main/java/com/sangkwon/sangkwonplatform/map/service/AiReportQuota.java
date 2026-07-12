package com.sangkwon.sangkwonplatform.map.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 회원별 AI 리포트 생성 한도를 공유 DB 카운터(AI_REPORT_QUOTA)로 원자 선점한다.
// 리포트 행 수를 세는 방식은 카운트와 저장 사이가 벌어져 동시 요청이 한도를 넘겼다(TOCTOU).
// 여기서는 카운터 행을 MERGE로 +1 하고 그 행을 잠근 채 확인해, 한도 초과 통과가 없게 직렬화한다(ApiUsageService.reserve와 같은 패턴).
@Component
public class AiReportQuota {

    private static final DateTimeFormatter HOUR_KEY = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jt;

    public AiReportQuota(JdbcTemplate jt) {
        this.jt = jt;
    }

    // 시간당(무료·Pro 공통)·무료 월 슬롯을 한 트랜잭션에서 선점한다. 초과면 예외로 롤백해 방금 올린 증분을 되돌린다.
    // 선점 후 실제 생성이 실패해도 반납하지 않는다(전역 Gemini 예약과 같은 규약: 시도 기준 차감).
    @Transactional
    public void reserve(Long memberId, boolean pro, int hourlyLimit, int freeMonthlyLimit) {
        LocalDateTime now = LocalDateTime.now();
        if (bumpAndCount(memberId + ":H" + now.format(HOUR_KEY)) > hourlyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "AI 리포트를 너무 자주 생성했어요. 잠시 후 다시 시도해 주세요.");
        }
        if (!pro && bumpAndCount(memberId + ":M" + now.format(MONTH_KEY)) > freeMonthlyLimit) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "이번 달 무료 AI 리포트 " + freeMonthlyLimit + "회를 모두 사용했어요. Pro로 업그레이드하면 무제한으로 생성할 수 있어요.");
        }
    }

    // 키의 카운터를 +1(없으면 1로 생성)하고 그 값을 돌려준다. MERGE가 그 행을 잠가 동시 호출이 여기서 직렬화된다.
    private long bumpAndCount(String key) {
        jt.update("""
                MERGE INTO AI_REPORT_QUOTA t USING (SELECT ? AS K FROM DUAL) s ON (t.QUOTA_KEY = s.K)
                WHEN MATCHED THEN UPDATE SET CNT = CNT + 1, UPDATED_AT = SYSTIMESTAMP
                WHEN NOT MATCHED THEN INSERT (QUOTA_KEY, CNT, UPDATED_AT) VALUES (s.K, 1, SYSTIMESTAMP)
                """, key);
        Long cnt = jt.queryForObject("SELECT CNT FROM AI_REPORT_QUOTA WHERE QUOTA_KEY = ?", Long.class, key);
        return cnt == null ? 0 : cnt;
    }

    // 오래된 카운터 행 정리(스케줄러). 지난 달·지난 시간 키는 다시 쓰이지 않으므로 넉넉히 남기고 지운다.
    public int purgeOlderThan(Duration retention) {
        return jt.update("DELETE FROM AI_REPORT_QUOTA WHERE UPDATED_AT < ?",
                Timestamp.valueOf(LocalDateTime.now().minus(retention)));
    }
}
