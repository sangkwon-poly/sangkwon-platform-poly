package com.sangkwon.sangkwonplatform.member.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 구독 축소(단건 환불)·탈퇴 시 구독 정리 등 회원 도메인 규칙 검증.
class MemberTest {

    private Member member() {
        return Member.create("user", "hash", "user@test.com", "회원");
    }

    @Test
    void 월간_환불은_1개월만_차감하고_남은_구독은_유지한다() {
        Member m = member();
        m.activatePro(LocalDateTime.now().plusMonths(2)); // 월간 2건이 누적된 상태

        m.reduceSubscription(BillingCycle.MONTHLY);

        // 1개월만 차감 -> 아직 약 1개월 남아 Pro 유지(단건 환불이 전체 구독을 지우지 않는다)
        assertThat(m.isPro()).isTrue();
        assertThat(m.getPlanUntil()).isBetween(LocalDateTime.now().plusDays(20), LocalDateTime.now().plusDays(40));
    }

    @Test
    void 남은_기간보다_큰_환불이면_무료로_내린다() {
        Member m = member();
        m.activatePro(LocalDateTime.now().plusMonths(1)); // 월간 1건

        m.reduceSubscription(BillingCycle.MONTHLY);

        assertThat(m.isPro()).isFalse();
        assertThat(m.getPlanUntil()).isNull();
        assertThat(m.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void 구독이_없으면_환불_축소는_아무것도_하지_않는다() {
        Member m = member(); // planUntil null

        m.reduceSubscription(BillingCycle.YEARLY);

        assertThat(m.getPlanUntil()).isNull();
        assertThat(m.isPro()).isFalse();
    }

    @Test
    void 탈퇴하면_구독_자격도_함께_정리된다() {
        Member m = member();
        m.activatePro(LocalDateTime.now().plusYears(1)); // 유효 Pro

        m.withdraw();

        assertThat(m.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(m.isPro()).isFalse();
        assertThat(m.getPlanUntil()).isNull();
        assertThat(m.getRole()).isEqualTo(Role.USER);
    }
}
