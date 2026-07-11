package com.sangkwon.sangkwonplatform.admin.pay.dto.response;

// 결제·구독 화면 상단 지표와 상태 필터 칩 카운트.
// 매출·결제 건수는 이번 달 승인(approvedAt) 기준, 상태별 카운트는 전체 기간 기준.
public record PaymentSummaryResponse(
        long monthRevenue,
        long monthPaidCount,
        long activeProCount,
        long totalCount,
        long pendingCount,
        long paidCount,
        long failedCount,
        long canceledCount
) {
}
