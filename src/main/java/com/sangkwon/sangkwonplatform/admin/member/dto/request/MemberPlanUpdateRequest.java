package com.sangkwon.sangkwonplatform.admin.member.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// 구독 수동 조작 요청. EXTEND는 months만큼 부여·연장(무료 회원이면 지금부터), REVOKE는 즉시 회수.
public record MemberPlanUpdateRequest(
        @NotNull(message = "구독 작업을 선택해 주세요!") PlanOp op,
        @Min(value = 1, message = "개월 수는 1~12 사이로 입력해 주세요!")
        @Max(value = 12, message = "개월 수는 1~12 사이로 입력해 주세요!") Integer months
) {
    public enum PlanOp { EXTEND, REVOKE }

    // months 생략 시 1개월. CS 보상의 기본 단위.
    public int monthsOrDefault() {
        return months == null ? 1 : months;
    }
}
