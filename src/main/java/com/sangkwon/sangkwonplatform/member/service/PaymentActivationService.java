package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.dto.response.PaymentConfirmResponse;
import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.MemberStatus;
import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 결제 승인 확정(주문 PAID)과 구독 활성화를 하나의 트랜잭션으로 묶어 원자적으로 반영한다.
// 토스 승인 HTTP는 이 메서드 밖에서 끝난 뒤 호출된다(외부 HTTP 동안 DB 트랜잭션을 잡지 않기 위함).
//
// 왜 별도 빈인가: 결제 승인 흐름(PaymentService.confirm, 관리자 대사 AdminPaymentService.reconcile)은
// 트랜잭션 밖에서 실행되어, 예전에는 '주문 PAID 저장'과 '회원 Pro 전환'이 서로 다른 트랜잭션으로 커밋됐다.
// 그 사이에 장애가 나면 주문은 PAID인데 회원은 Pro가 아닌 불일치가 남고, 재시도는 PAID 멱등 분기에 걸려
// 복구되지 않았다. 두 쓰기를 한 트랜잭션으로 묶으면 둘 다 커밋되거나 둘 다 롤백돼 그 상태가 원천 차단된다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentActivationService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final MemberRepository memberRepository;

    // 승인된 주문을 PAID로 확정하고 같은 트랜잭션에서 구독을 켠다.
    // 이미 PAID면 멱등하게 현재 상태를 돌려준다(구독은 PAID로 만든 트랜잭션에서 함께 반영됐다).
    // 동시 승인으로 @Version 충돌이 나면 save에서 OptimisticLockingFailureException이 나 트랜잭션이 롤백되며,
    // 호출부가 이를 잡아 승자 상태로 멱등 응답한다.
    @Transactional
    public PaymentConfirmResponse finalizePaid(PaymentOrder order, String paymentKey, LocalDateTime approvedAt) {
        if (order.getStatus() == PaymentStatus.PAID) {
            return PaymentConfirmResponse.from(order);
        }
        // 종결 상태(환불 완료 CANCELED 등)를 승인으로 되돌리지 않는다. 되돌리면 환불이 무효화되고
        // 구독이 무료로 재부여된다. 승인/대사 두 경로가 이 가드를 공유하므로 어느 경로로 와도 안전하다.
        if (!order.getStatus().canTransitionToPaid()) {
            log.warn("승인 확정을 건너뜀: 주문 {}가 {} 상태라 PAID로 전이할 수 없음", order.getOrderId(), order.getStatus());
            return PaymentConfirmResponse.from(order);
        }
        order.paid(paymentKey, approvedAt);
        paymentOrderRepository.save(order);
        activateSubscription(order.getMemberId(), order.getBillingCycle());
        return PaymentConfirmResponse.from(order);
    }

    // 결제 확정과 같은 트랜잭션에서 구독을 켠다. 만료 전 재구독이면 남은 기간에 이어붙여 손해가 없게 한다.
    // 회원이 없으면(하드삭제된 주문 대사 등) 결제는 이미 이뤄졌으니 주문 PAID는 유지하고 활성화만 건너뛴다.
    private void activateSubscription(Long memberId, BillingCycle cycle) {
        if (memberId == null) {
            log.warn("결제가 확정됐으나 회원이 없어(memberId=null) 구독 활성화를 건너뜀");
            return;
        }
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            log.warn("결제가 확정됐으나 회원({})을 찾을 수 없어 구독 활성화를 건너뜀", memberId);
            return;
        }
        // 탈퇴·정지·휴면 회원은 구독을 부활시키지 않는다(탈퇴 계정을 대사하면 익명화된 계정에 Pro가 되살아나
        // '구독중' 지표를 부풀린다). 결제 자체는 이뤄졌으니 주문 PAID는 유지하고 활성화만 건너뛴다.
        if (member.getStatus() != MemberStatus.ACTIVE) {
            log.warn("결제가 확정됐으나 회원({})이 비활성({}) 상태라 구독 활성화를 건너뜀", memberId, member.getStatus());
            return;
        }
        LocalDateTime base = member.isPro() ? member.getPlanUntil() : LocalDateTime.now();
        LocalDateTime until = cycle == BillingCycle.YEARLY ? base.plusYears(1) : base.plusMonths(1);
        member.activatePro(until);
        memberRepository.save(member);
    }

    // 환불/취소 확정: 주문 CANCELED 저장과 (활성화된 적 있으면) 구독 회수를 한 트랜잭션으로 묶는다.
    // 둘이 따로 커밋되면 회수가 실패한 뒤 재시도가 CANCELED 멱등 분기에 걸려 Pro가 잔존하는 구멍이 생긴다.
    // wasActivated: 이 주문이 PAID였는지(=구독 기간을 부여한 적 있는지). 호출부가 판단해 넘긴다.
    @Transactional
    public void finalizeCanceled(PaymentOrder order, boolean wasActivated) {
        if (order.getStatus() != PaymentStatus.CANCELED) {
            order.canceled();
            paymentOrderRepository.save(order);
        }
        if (wasActivated && order.getMemberId() != null) {
            memberRepository.findById(order.getMemberId()).ifPresent(member -> {
                member.reduceSubscription(order.getBillingCycle());
                memberRepository.save(member);
            });
        }
    }
}
