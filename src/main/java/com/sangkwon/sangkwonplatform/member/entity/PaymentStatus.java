package com.sangkwon.sangkwonplatform.member.entity;

// 결제 주문 상태. PENDING(결제창 진입) -> PAID(승인 완료) / FAILED(승인 실패), PAID -> CANCELED(환불)
public enum PaymentStatus {
    PENDING, PAID, FAILED, CANCELED
}
