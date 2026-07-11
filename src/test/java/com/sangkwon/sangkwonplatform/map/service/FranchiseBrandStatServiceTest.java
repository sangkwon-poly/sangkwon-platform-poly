package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.response.FranchiseBrandStatResponse;
import com.sangkwon.sangkwonplatform.map.entity.FranchiseBrandStat;
import com.sangkwon.sangkwonplatform.map.repository.FranchiseBrandStatRepository;
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

// 주요 프랜차이즈 조회의 Pro 게이팅을 검증한다. 비Pro는 402로 막고, Pro만 조회가 진행된다.
@ExtendWith(MockitoExtension.class)
class FranchiseBrandStatServiceTest {

    @Mock FranchiseBrandStatRepository repository;
    @Mock MemberRepository memberRepository;
    @InjectMocks FranchiseBrandStatService service;

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void 무료_회원은_402를_던지고_브랜드를_조회하지_않는다() {
        Member free = Member.create("user", "hash", "user@test.com", "회원");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(free));

        assertThatThrownBy(() -> service.getTopBrands(1L, "CS100007"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(402));

        verify(repository, never()).findTop5ByIndutyCdOrderByFrcsCntDescBrandNmAsc(any());
    }

    @Test
    void Pro_회원은_상위_브랜드_목록을_받는다() {
        Member pro = Member.create("user", "hash", "user@test.com", "회원");
        pro.activatePro(LocalDateTime.now().plusMonths(1));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(pro));
        FranchiseBrandStat stat = mock(FranchiseBrandStat.class);
        when(stat.getBrandNm()).thenReturn("나브랜드");
        when(stat.getFrcsCnt()).thenReturn(500L);
        when(stat.getAvgSalesAmt()).thenReturn(620000L);
        when(repository.findTop5ByIndutyCdOrderByFrcsCntDescBrandNmAsc("CS100007")).thenReturn(List.of(stat));

        List<FranchiseBrandStatResponse> res = service.getTopBrands(1L, "CS100007");

        assertThat(res).hasSize(1);
        assertThat(res.get(0).brandNm()).isEqualTo("나브랜드");
        assertThat(res.get(0).frcsCnt()).isEqualTo(500L);
        assertThat(res.get(0).avgSalesAmt()).isEqualTo(620000L);
    }
}
