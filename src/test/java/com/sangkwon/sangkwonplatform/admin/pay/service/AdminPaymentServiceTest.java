package com.sangkwon.sangkwonplatform.admin.pay.service;

import com.sangkwon.sangkwonplatform.admin.pay.dto.response.AdminPaymentResponse;
import com.sangkwon.sangkwonplatform.admin.pay.dto.response.PaymentPageResponse;
import com.sangkwon.sangkwonplatform.admin.pay.dto.response.PaymentSummaryResponse;
import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import com.sangkwon.sangkwonplatform.member.service.PaymentActivationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// AdminPaymentService 단위 테스트. 회원 검색 분기와 표시명 매핑, 요약 집계 조합을 검증.
@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceTest {

    @Mock PaymentOrderRepository paymentOrderRepository;
    @Mock MemberRepository memberRepository;
    @InjectMocks AdminPaymentService adminPaymentService;

    private final Pageable pageable = PageRequest.of(0, 20);

    private PaymentOrder order(String orderId, Long memberId) {
        return PaymentOrder.create(orderId, memberId, "PRO", BillingCycle.YEARLY, 240_000L, "여기콕 Pro 연간");
    }

    private PaymentOrderRepository.PaymentStatusCount count(PaymentStatus status, long cnt) {
        return new PaymentOrderRepository.PaymentStatusCount() {
            @Override public PaymentStatus getStatus() { return status; }
            @Override public long getCnt() { return cnt; }
        };
    }

    @Test
    @DisplayName("목록: 검색어가 없으면 전체 검색 경로를 타고 회원 표시명을 채운다")
    void getOrders_withoutKeyword() {
        // Member.create는 저장 전이라 memberId가 없다. 조회 결과처럼 ID가 채워진 회원을 목으로 만든다.
        Member hong = org.mockito.Mockito.mock(Member.class);
        when(hong.getMemberId()).thenReturn(1L);
        when(hong.getLoginId()).thenReturn("hong");
        when(hong.getNickname()).thenReturn("홍길동");
        when(paymentOrderRepository.searchForAdmin(isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order("o1", 1L)), pageable, 1));
        when(memberRepository.findAllById(List.of(1L))).thenReturn(List.of(hong));

        PaymentPageResponse res = adminPaymentService.getOrders(null, null, null, pageable);

        assertThat(res.totalElements()).isEqualTo(1);
        assertThat(res.content().get(0).loginId()).isEqualTo("hong");
        assertThat(res.content().get(0).nickname()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("목록: 검색어에 걸리는 회원이 없으면 주문 조회 없이 빈 페이지를 돌려준다")
    void getOrders_noMemberMatch() {
        when(memberRepository.findIdsByKeyword(eq("%없는사람%"), any(Pageable.class))).thenReturn(List.of());

        PaymentPageResponse res = adminPaymentService.getOrders("없는사람", null, null, pageable);

        assertThat(res.content()).isEmpty();
        assertThat(res.totalElements()).isZero();
        verify(paymentOrderRepository, never()).searchForAdminByMembers(any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("목록: 회원이 하드삭제된 주문(memberId null)은 표시명 없이도 행이 나온다")
    void getOrders_orphanOrder() {
        when(paymentOrderRepository.searchForAdmin(isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order("o1", null)), pageable, 1));
        when(memberRepository.findAllById(List.of())).thenReturn(List.of());

        PaymentPageResponse res = adminPaymentService.getOrders(null, null, null, pageable);

        assertThat(res.content().get(0).loginId()).isNull();
        assertThat(res.content().get(0).amount()).isEqualTo(240_000L);
    }

    @Test
    @DisplayName("요약: 상태별 카운트에 없는 상태는 0으로 채우고 매출·구독자를 함께 담는다")
    void getSummary() {
        when(paymentOrderRepository.countGroupByStatus())
                .thenReturn(List.of(count(PaymentStatus.PAID, 4), count(PaymentStatus.PENDING, 3)));
        when(paymentOrderRepository.sumAmountByStatusSince(eq(PaymentStatus.PAID), any())).thenReturn(960_000L);
        when(paymentOrderRepository.countByStatusAndApprovedAtGreaterThanEqual(eq(PaymentStatus.PAID), any()))
                .thenReturn(4L);
        when(memberRepository.countByPlanUntilAfter(any())).thenReturn(4L);

        PaymentSummaryResponse res = adminPaymentService.getSummary();

        assertThat(res.monthRevenue()).isEqualTo(960_000L);
        assertThat(res.monthPaidCount()).isEqualTo(4);
        assertThat(res.activeProCount()).isEqualTo(4);
        assertThat(res.totalCount()).isEqualTo(7);
        assertThat(res.pendingCount()).isEqualTo(3);
        assertThat(res.paidCount()).isEqualTo(4);
        assertThat(res.failedCount()).isZero();
    }

    @Test
    @DisplayName("검색어 정규화: 소문자화하고 LIKE 와일드카드를 이스케이프한다")
    void getOrders_keywordNormalize() {
        when(memberRepository.findIdsByKeyword(eq("%hong\\_1%"), any(Pageable.class))).thenReturn(List.of(1L));
        when(paymentOrderRepository.searchForAdminByMembers(isNull(), isNull(), eq(List.of(1L)), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        adminPaymentService.getOrders("HONG_1", null, null, pageable);

        verify(memberRepository).findIdsByKeyword(eq("%hong\\_1%"), any(Pageable.class));
    }

    // 환불은 토스 취소 API를 호출하므로 실제 RestClient를 MockRestServiceServer로 스텁한다.
    private static final String CANCEL_URL = "https://api.tosspayments.com/v1/payments/pk-1/cancel";
    private MockRestServiceServer tossServer;

    private AdminPaymentService serviceWithToss() {
        RestClient.Builder builder = RestClient.builder();
        tossServer = MockRestServiceServer.bindTo(builder).build();
        PaymentActivationService activationService =
                new PaymentActivationService(paymentOrderRepository, memberRepository);
        return new AdminPaymentService(paymentOrderRepository, memberRepository, activationService,
                builder.build(), "test-sk");
    }

    private static PaymentOrder paidOrder() {
        PaymentOrder o = PaymentOrder.create("o1", 1L, "PRO", BillingCycle.YEARLY, 240_000L, "여기콕 Pro 연간");
        o.paid("pk-1", LocalDateTime.now());
        LocalDateTime startedAt = LocalDateTime.now();
        o.recordSubscriptionGrant(startedAt, startedAt.plusYears(1));
        return o;
    }

    private static Member proMember() {
        Member m = Member.create("user", "hash", "user@test.com", "회원");
        m.activatePro(LocalDateTime.now().plusMonths(12));
        return m;
    }

    @Test
    @DisplayName("환불: PAID 주문이면 토스 취소 후 CANCELED로 바꾸고 Pro를 회수한다")
    void cancel_paidOrder() {
        PaymentOrder order = paidOrder();
        Member member = proMember();
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        tossServer.expect(requestTo(CANCEL_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        AdminPaymentResponse res = svc.cancel("o1", "중복 결제");

        assertThat(res.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(member.isPro()).isFalse();
        verify(paymentOrderRepository).save(order);
        verify(memberRepository).save(member);
        tossServer.verify();
    }

    @Test
    @DisplayName("환불: PAID가 아니면 토스 호출 없이 거절한다")
    void cancel_rejectsNonPaid() {
        PaymentOrder pending = PaymentOrder.create("o1", 1L, "PRO", BillingCycle.YEARLY, 240_000L, "여기콕 Pro 연간");
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> svc.cancel("o1", "사유"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentOrderRepository, never()).save(any());
        tossServer.verify();
    }

    @Test
    @DisplayName("환불: 이미 CANCELED면 토스 호출 없이 멱등하게 응답한다")
    void cancel_idempotentWhenAlreadyCanceled() {
        PaymentOrder order = paidOrder();
        order.canceled();
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(proMember()));

        AdminPaymentResponse res = svc.cancel("o1", "사유");

        assertThat(res.status()).isEqualTo(PaymentStatus.CANCELED);
        verify(paymentOrderRepository, never()).save(any());
        tossServer.verify();
    }

    @Test
    @DisplayName("환불: 토스 취소가 실패하면 502를 던지고 상태를 바꾸지 않는다")
    void cancel_tossFailureKeepsPaid() {
        PaymentOrder order = paidOrder();
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1")).thenReturn(Optional.of(order));
        tossServer.expect(requestTo(CANCEL_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> svc.cancel("o1", "사유"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentOrderRepository, never()).save(any());
        tossServer.verify();
    }

    @Test
    @DisplayName("환불: 토스가 이미 취소됨이면 상태만 맞추고 환불 처리한다")
    void cancel_alreadyCanceledAtToss() {
        PaymentOrder order = paidOrder();
        Member member = proMember();
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        tossServer.expect(requestTo(CANCEL_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"code\":\"ALREADY_CANCELED_PAYMENT\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        AdminPaymentResponse res = svc.cancel("o1", "사유");

        assertThat(res.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(member.isPro()).isFalse();
        verify(paymentOrderRepository).save(order);
        tossServer.verify();
    }

    private static final String QUERY_URL = "https://api.tosspayments.com/v1/payments/orders/o1";

    private static PaymentOrder failedOrder() {
        PaymentOrder o = PaymentOrder.create("o1", 1L, "PRO", BillingCycle.YEARLY, 240_000L, "여기콕 Pro 연간");
        o.failed();
        return o;
    }

    private static PaymentOrder pendingOrder() {
        return PaymentOrder.create("o1", 1L, "PRO", BillingCycle.YEARLY, 240_000L, "여기콕 Pro 연간");
    }

    @Test
    @DisplayName("대사: FAILED 주문인데 토스가 DONE이면 PAID로 정정하고 구독을 켠다")
    void reconcile_recoversLostPayment() {
        PaymentOrder order = failedOrder();
        Member member = Member.create("user", "hash", "user@test.com", "회원");
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        tossServer.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("{\"status\":\"DONE\",\"paymentKey\":\"pk-x\",\"approvedAt\":\"2026-07-11T00:00:00+09:00\"}",
                        MediaType.APPLICATION_JSON));

        AdminPaymentService.ReconcileResult res = svc.reconcile("o1");

        assertThat(res.before()).isEqualTo(PaymentStatus.FAILED);
        assertThat(res.after()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(member.isPro()).isTrue();
        verify(paymentOrderRepository).save(order);
        tossServer.verify();
    }

    @Test
    @DisplayName("대사: PENDING인데 토스에 결제 기록이 없으면 FAILED로 확정한다")
    void reconcile_notFoundBecomesFailed() {
        PaymentOrder order = pendingOrder();
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(Member.create("user", "hash", "user@test.com", "회원")));
        tossServer.expect(requestTo(QUERY_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"code\":\"NOT_FOUND_PAYMENT\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        AdminPaymentService.ReconcileResult res = svc.reconcile("o1");

        assertThat(res.after()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentOrderRepository).save(order);
        tossServer.verify();
    }

    @Test
    @DisplayName("대사: 토스가 진행 중이면 상태를 바꾸지 않는다")
    void reconcile_inProgressNoChange() {
        PaymentOrder order = pendingOrder();
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(Member.create("user", "hash", "user@test.com", "회원")));
        tossServer.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("{\"status\":\"IN_PROGRESS\"}", MediaType.APPLICATION_JSON));

        AdminPaymentService.ReconcileResult res = svc.reconcile("o1");

        assertThat(res.before()).isEqualTo(PaymentStatus.PENDING);
        assertThat(res.after()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentOrderRepository, never()).save(any());
        tossServer.verify();
    }

    @Test
    @DisplayName("대사: PAID인데 토스가 CANCELED면 CANCELED로 정정하고 Pro를 회수한다")
    void reconcile_paidCanceledAtToss() {
        PaymentOrder order = paidOrder();
        Member member = proMember();
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        tossServer.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("{\"status\":\"CANCELED\"}", MediaType.APPLICATION_JSON));

        AdminPaymentService.ReconcileResult res = svc.reconcile("o1");

        assertThat(res.after()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(member.isPro()).isFalse();
        verify(paymentOrderRepository).save(order);
        tossServer.verify();
    }

    @Test
    @DisplayName("대사: 환불 회수 저장 충돌 후 재대사하면 CANCELED로 수렴한다")
    void reconcile_recoversCancelAfterOptimisticLockFailure() {
        PaymentOrder failedOrder = paidOrder();
        PaymentOrder persistedOrder = paidOrder();
        Member failedMember = proMember();
        Member persistedMember = proMember();
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1"))
                .thenReturn(Optional.of(failedOrder), Optional.of(persistedOrder));
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(failedMember), Optional.of(persistedMember));
        when(memberRepository.save(any(Member.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        tossServer.expect(requestTo(CANCEL_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        tossServer.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("{\"status\":\"CANCELED\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> svc.cancel("o1", "중복 결제"))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(persistedOrder.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(persistedMember.isPro()).isTrue();

        AdminPaymentService.ReconcileResult res = svc.reconcile("o1");

        assertThat(res.before()).isEqualTo(PaymentStatus.PAID);
        assertThat(res.after()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(persistedOrder.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(persistedMember.isPro()).isFalse();
        tossServer.verify();
    }

    @Test
    @DisplayName("대사: PENDING인데 토스가 CANCELED면 CANCELED로만 정정하고 구독은 회수하지 않는다")
    void reconcile_pendingCanceledDoesNotRevoke() {
        PaymentOrder order = pendingOrder(); // 한 번도 활성화(PAID)된 적 없는 주문
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1")).thenReturn(Optional.of(order));
        tossServer.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("{\"status\":\"CANCELED\"}", MediaType.APPLICATION_JSON));

        AdminPaymentService.ReconcileResult res = svc.reconcile("o1");

        assertThat(res.before()).isEqualTo(PaymentStatus.PENDING);
        assertThat(res.after()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        // 부여한 적 없는 주문이므로 구독 회수(member 저장)가 일어나지 않는다.
        // toResponse가 표시용으로 findById를 호출하므로 findById가 아니라 save로 회수 여부를 검증한다.
        verify(memberRepository, never()).save(any());
        verify(paymentOrderRepository).save(order);
        tossServer.verify();
    }

    @Test
    @DisplayName("대사: PAID인데 토스가 만료라 해도 강등하지 않는다")
    void reconcile_paidNotDowngraded() {
        PaymentOrder order = paidOrder();
        AdminPaymentService svc = serviceWithToss();
        when(paymentOrderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(proMember()));
        tossServer.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("{\"status\":\"EXPIRED\"}", MediaType.APPLICATION_JSON));

        AdminPaymentService.ReconcileResult res = svc.reconcile("o1");

        assertThat(res.after()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentOrderRepository, never()).save(any());
        tossServer.verify();
    }
}
