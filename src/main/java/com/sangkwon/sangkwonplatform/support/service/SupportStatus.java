package com.sangkwon.sangkwonplatform.support.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 신청기간으로 모집 상태와 D데이를 계산한다. 공개 목록과 관리자 목록이 같은 규칙을 쓰도록 한곳에 둔다.
public final class SupportStatus {

    // 마감이 이 일수 이내면 마감 임박
    private static final int CLOSING_DAYS = 7;

    private SupportStatus() {
    }

    public static String of(LocalDate bgn, LocalDate end, String periodRaw, String recruitYn, LocalDate today) {
        if (end != null) {
            if (bgn != null && bgn.isAfter(today)) {
                return "UPCOMING";
            }
            if (end.isBefore(today)) {
                return "CLOSED";
            }
            return ChronoUnit.DAYS.between(today, end) <= CLOSING_DAYS ? "CLOSING" : "RECRUITING";
        }
        // 마감일이 없으면 모집 플래그(K-Startup)나 상시 원문(기업마당)으로 판단한다.
        // 둘 다 없는 오래된 공고는 마감으로 본다(마감일 null이라고 진행중이 아니다).
        if ("Y".equals(recruitYn)) {
            return "RECRUITING";
        }
        if (periodRaw != null) {
            return "ALWAYS";
        }
        return "CLOSED";
    }

    public static Integer dday(LocalDate end, LocalDate today) {
        return end == null ? null : (int) ChronoUnit.DAYS.between(today, end);
    }
}
