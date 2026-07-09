package com.sangkwon.sangkwonplatform.support.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 신청기간으로 모집 상태와 D데이를 계산한다. 공개 목록과 관리자 목록이 같은 규칙을 쓰도록 한곳에 둔다.
public final class SupportStatus {

    // 마감이 이 일수 이내면 마감 임박
    private static final int CLOSING_DAYS = 7;

    private SupportStatus() {
    }

    public static String of(LocalDate bgn, LocalDate end, String periodRaw, LocalDate today) {
        if (end == null) {
            return periodRaw != null ? "ALWAYS" : "RECRUITING";
        }
        if (bgn != null && bgn.isAfter(today)) {
            return "UPCOMING";
        }
        if (end.isBefore(today)) {
            return "CLOSED";
        }
        return ChronoUnit.DAYS.between(today, end) <= CLOSING_DAYS ? "CLOSING" : "RECRUITING";
    }

    public static Integer dday(LocalDate end, LocalDate today) {
        return end == null ? null : (int) ChronoUnit.DAYS.between(today, end);
    }
}
