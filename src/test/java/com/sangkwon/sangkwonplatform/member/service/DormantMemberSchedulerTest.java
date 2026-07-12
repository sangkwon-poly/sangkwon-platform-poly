package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 휴면 자동 전환: 설정 임계일 기준 컷오프로 리포지토리를 호출하고, 실패해도 예외를 삼킨다.
@ExtendWith(MockitoExtension.class)
class DormantMemberSchedulerTest {

    @Mock MemberRepository memberRepository;
    @InjectMocks DormantMemberScheduler scheduler;

    @Test
    void 임계일_기준_컷오프로_휴면_전환을_호출한다() {
        ReflectionTestUtils.setField(scheduler, "inactiveDays", 365L);
        when(memberRepository.markInactiveMembersDormant(any(), any())).thenReturn(3);

        scheduler.transitionInactiveToDormant();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(memberRepository).markInactiveMembersDormant(cutoff.capture(), now.capture());
        // 컷오프는 대략 365일 전(364~366일 범위로 넉넉히 확인), now는 유효 Pro 제외 기준(현재 시각)
        assertThat(cutoff.getValue())
                .isBefore(LocalDateTime.now().minusDays(364))
                .isAfter(LocalDateTime.now().minusDays(366));
        assertThat(now.getValue()).isBetween(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1));
    }

    @Test
    void 리포지토리_오류는_삼켜서_다음_주기에_맡긴다() {
        ReflectionTestUtils.setField(scheduler, "inactiveDays", 365L);
        when(memberRepository.markInactiveMembersDormant(any(), any()))
                .thenThrow(new RuntimeException("DB 접근 실패"));

        assertThatCode(() -> scheduler.transitionInactiveToDormant()).doesNotThrowAnyException();
    }
}
