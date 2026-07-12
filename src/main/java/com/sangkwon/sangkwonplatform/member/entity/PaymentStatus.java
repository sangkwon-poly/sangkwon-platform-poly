package com.sangkwon.sangkwonplatform.member.entity;

// 결제 주문 상태. PENDING(결제창 진입) -> PAID(승인 완료) / FAILED(승인 실패), PAID -> CANCELED(환불)
public enum PaymentStatus {
    PENDING, PAID, FAILED, CANCELED;

    // PAID로 확정할 수 있는 출발 상태인가. 승인 대기(PENDING)와 유실 복구 대상(FAILED)만 허용한다.
    // 환불 완료(CANCELED)를 다시 PAID로 되돌리면 환불이 무효화되고 Pro가 무료 재부여되므로 막는다.
    public boolean canTransitionToPaid() {
        return this == PENDING || this == FAILED;
    }
}
