package com.sangkwon.sangkwonplatform.admin.pay.service;

import com.sangkwon.sangkwonplatform.admin.pay.dto.response.PaymentPageResponse;
import com.sangkwon.sangkwonplatform.admin.pay.dto.response.PaymentSummaryResponse;
import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
