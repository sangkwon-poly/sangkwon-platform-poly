package com.sangkwon.sangkwonplatform.member.entity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 구독 축소(단건 환불)·탈퇴 시 구독 정리 등 회원 도메인 규칙 검증.
class MemberTest {

    private Member member() {
        return Member.create("user", "hash", "user@test.com", "회원");
    }

    @Test
    void 환불은_주문이_실제로_부여한_기간만_차감한다() {
        Member m = member();
        LocalDateTime grantedFrom = LocalDateTime.of(2026, 1, 31, 12, 0);
        LocalDateTime grantedUntil = grantedFrom.plusMonths(1);
        LocalDateTime planUntil = LocalDateTime.now().plusMonths(2);
        m.activatePro(planUntil);

        m.reduceSubscription(grantedFrom, grantedUntil);

        assertThat(m.isPro()).isTrue();
        assertThat(m.getPlanUntil()).isEqualTo(planUntil.minus(Duration.between(grantedFrom, grantedUntil)));
    }

    @Test
    void 남은_기간보다_큰_환불이면_무료로_내린다() {
        Member m = member();
        LocalDateTime grantedFrom = LocalDateTime.now();
        LocalDateTime grantedUntil = grantedFrom.plusMonths(1);
        m.activatePro(grantedUntil);

        m.reduceSubscription(grantedFrom, grantedUntil);

        assertThat(m.isPro()).isFalse();
        assertThat(m.getPlanUntil()).isNull();
        assertThat(m.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void 구독이_없으면_환불_축소는_아무것도_하지_않는다() {
        Member m = member(); // planUntil null

        LocalDateTime grantedFrom = LocalDateTime.now();
        m.reduceSubscription(grantedFrom, grantedFrom.plusYears(1));

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
