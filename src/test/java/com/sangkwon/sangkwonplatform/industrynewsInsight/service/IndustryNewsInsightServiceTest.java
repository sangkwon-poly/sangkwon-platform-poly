package com.sangkwon.sangkwonplatform.industrynewsInsight.service;

import com.sangkwon.sangkwonplatform.industrynewsInsight.dto.response.IndustryNewsInsightResponse;
import com.sangkwon.sangkwonplatform.industrynewsInsight.repository.IndustryNewsInsightRepository;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 업종·상권 동향 조회의 Pro 게이팅을 검증한다. 비Pro는 402로 막고, Pro만 조회가 진행된다.
@ExtendWith(MockitoExtension.class)
class IndustryNewsInsightServiceTest {

    @Mock IndustryNewsInsightRepository repository;
    @Mock MemberRepository memberRepository;
    @InjectMocks IndustryNewsInsightService service;

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void 무료_회원은_402를_던지고_인사이트를_조회하지_않는다() {
        Member free = Member.create("user", "hash", "user@test.com", "회원");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(free));

        assertThatThrownBy(() -> service.getLatestInsight(1L, "CS100001"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(402));

        verify(repository, never()).findFirstByIndutyCdOrderByYearMonthDesc(any());
    }

    @Test
    void Pro_회원은_최신_인사이트를_조회한다() {
        Member pro = Member.create("user", "hash", "user@test.com", "회원");
        pro.activatePro(LocalDateTime.now().plusMonths(1));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(pro));
        when(repository.findFirstByIndutyCdOrderByYearMonthDesc("CS100001")).thenReturn(Optional.empty());

        IndustryNewsInsightResponse res = service.getLatestInsight(1L, "CS100001");

        assertThat(res.indutyCd()).isEqualTo("CS100001");
    }
}
