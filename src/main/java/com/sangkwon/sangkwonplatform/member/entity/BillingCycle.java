package com.sangkwon.sangkwonplatform.member.entity;

// 결제 주기. 금액 산정은 PaymentService가 서버에서 정한다(클라이언트 금액을 신뢰하지 않음).
public enum BillingCycle {
    MONTHLY, YEARLY
}
