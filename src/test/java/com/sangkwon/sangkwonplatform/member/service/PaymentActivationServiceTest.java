package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.dto.response.PaymentConfirmResponse;
import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 승인 확정(PAID)과 구독 활성화를 한 트랜잭션으로 묶는 원자화 빈의 핵심 동작을 검증한다.
// 트랜잭션 자체(롤백)는 @Transactional 계약에 맡기고, 여기서는 상태 전이/멱등/회원부재 내성을 본다.
class PaymentActivationServiceTest {

    private final PaymentOrderRepository paymentOrderRepository = mock(PaymentOrderRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final PaymentActivationService service =
            new PaymentActivationService(paymentOrderRepository, memberRepository);

    private static PaymentOrder pendingOrder() {
        return PaymentOrder.create("o1", 1L, "PRO", BillingCycle.YEARLY, 240_000L, "여기콕 Pro 연간");
    }

    @Test
    void PENDING_주문을_PAID로_확정하고_구독을_켠다() {
        PaymentOrder order = pendingOrder();
        Member member = Member.create("user", "hash", "user@test.com", "회원");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        PaymentConfirmResponse res = service.finalizePaid(order, "pk-1", LocalDateTime.now());

        assertThat(res.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getPaymentKey()).isEqualTo("pk-1");
        assertThat(member.isPro()).isTrue();
        verify(paymentOrderRepository).save(order);
        verify(memberRepository).save(member);
    }

    @Test
    void 이미_PAID면_멱등하게_응답하고_구독을_다시_켜지_않는다() {
        PaymentOrder order = pendingOrder();
        order.paid("pk-1", LocalDateTime.now()); // 이미 확정된 주문(성공 페이지 새로고침 등)

        PaymentConfirmResponse res = service.finalizePaid(order, "pk-1", LocalDateTime.now());

        assertThat(res.status()).isEqualTo(PaymentStatus.PAID);
        // 재활성화하면 만료가 계속 늘어나므로, 이미 PAID면 회원을 건드리지 않는다
        verify(memberRepository, never()).findById(any());
        verify(memberRepository, never()).save(any());
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void 회원이_없으면_PAID는_유지하고_활성화만_건너뛴다() {
        PaymentOrder order = pendingOrder();
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        PaymentConfirmResponse res = service.finalizePaid(order, "pk-1", LocalDateTime.now());

        // 결제는 이미 이뤄졌으니 주문 PAID는 확정하되, 줄 대상이 없으므로 활성화만 조용히 건너뛴다
        assertThat(res.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentOrderRepository).save(order);
        verify(memberRepository, never()).save(any());
    }
}
