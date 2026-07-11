package com.sangkwon.sangkwonplatform.industrytrademark.service;

import com.sangkwon.sangkwonplatform.industrytrademark.dto.response.IndustryTrademarkResponse;
import com.sangkwon.sangkwonplatform.industrytrademark.entity.IndustryTrademark;
import com.sangkwon.sangkwonplatform.industrytrademark.repository.IndustryTrademarkRepository;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 상표 출원 동향 조회의 Pro 게이팅을 검증한다. 비Pro는 402로 막고, Pro만 조회가 진행된다.
@ExtendWith(MockitoExtension.class)
class IndustryTrademarkServiceTest {

    @Mock IndustryTrademarkRepository repository;
    @Mock MemberRepository memberRepository;
    @InjectMocks IndustryTrademarkService service;

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void 무료_회원은_402를_던지고_상표를_조회하지_않는다() {
        Member free = Member.create("user", "hash", "user@test.com", "회원");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(free));

        assertThatThrownBy(() -> service.getRecentTrademarks(1L, "CS100007"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(402));

        verify(repository, never()).findTop5ByIndutyCdOrderByApplDateDescTmIdDesc(any());
    }

    @Test
    void Pro_회원은_최신_상표_목록을_받는다() {
        Member pro = Member.create("user", "hash", "user@test.com", "회원");
        pro.activatePro(LocalDateTime.now().plusMonths(1));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(pro));
        IndustryTrademark tm = mock(IndustryTrademark.class);
        when(tm.getTitle()).thenReturn("가상표");
        when(tm.getStatus()).thenReturn("등록");
        when(repository.findTop5ByIndutyCdOrderByApplDateDescTmIdDesc("CS100007")).thenReturn(List.of(tm));

        List<IndustryTrademarkResponse> res = service.getRecentTrademarks(1L, "CS100007");

        assertThat(res).hasSize(1);
        assertThat(res.get(0).title()).isEqualTo("가상표");
        assertThat(res.get(0).status()).isEqualTo("등록");
    }
}
