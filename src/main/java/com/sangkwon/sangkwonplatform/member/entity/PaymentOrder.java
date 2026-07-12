package com.sangkwon.sangkwonplatform.member.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 요금제 결제 주문. 주문 생성 시 서버가 금액을 확정해 저장하고, 승인 단계에서 이 금액과 대조해 위변조를 막는다.
@Entity
@Table(name = "PAYMENT_ORDER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder extends BaseEntity {

    @Id
    @Column(name = "ORDER_ID", length = 64)
    private String orderId;

    // 회원 하드삭제 시 스키마 FK가 ON DELETE SET NULL이라 null이 될 수 있다(결제 기록은 보존)
    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "PLAN_CD", nullable = false, length = 20)
    private String planCd;

    @Enumerated(EnumType.STRING)
    @Column(name = "BILLING_CYCLE", nullable = false, length = 10)
    private BillingCycle billingCycle;

    @Column(name = "AMOUNT", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "ORDER_NAME", nullable = false, length = 100)
    private String orderName;

    @Column(name = "PAYMENT_KEY", length = 200)
    private String paymentKey;

    @Column(name = "APPROVED_AT")
    private LocalDateTime approvedAt;

    @Column(name = "SUBSCRIPTION_STARTED_AT")
    private LocalDateTime subscriptionStartedAt;

    @Column(name = "SUBSCRIPTION_ENDED_AT")
    private LocalDateTime subscriptionEndedAt;

    // 동시 승인 경합에서 낡은 상태 저장이 PAID를 덮지 못하게 하는 낙관적 락 버전
    @Version
    @Column(name = "VERSION", nullable = false)
    private long version;

    public static PaymentOrder create(String orderId, Long memberId, String planCd,
                                      BillingCycle billingCycle, long amount, String orderName) {
        PaymentOrder o = new PaymentOrder();
        o.orderId = orderId;
        o.memberId = memberId;
        o.planCd = planCd;
        o.billingCycle = billingCycle;
        o.amount = amount;
        o.orderName = orderName;
        return o;
    }

    public void paid(String paymentKey, LocalDateTime approvedAt) {
        this.paymentKey = paymentKey;
        this.approvedAt = approvedAt;
        this.status = PaymentStatus.PAID;
    }

    public void failed() {
        this.status = PaymentStatus.FAILED;
    }

    public void recordSubscriptionGrant(LocalDateTime startedAt, LocalDateTime endedAt) {
        this.subscriptionStartedAt = startedAt;
        this.subscriptionEndedAt = endedAt;
    }

    public boolean hasSubscriptionGrant() {
        return subscriptionStartedAt != null && subscriptionEndedAt != null;
    }

    // 환불(토스 결제취소) 확정. 결제 기록과 paymentKey는 대사용으로 보존한다.
    public void canceled() {
        this.status = PaymentStatus.CANCELED;
    }
}
