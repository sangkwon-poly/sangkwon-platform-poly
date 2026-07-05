package com.sangkwon.sangkwonplatform.streetpop.service;

import com.sangkwon.sangkwonplatform.streetpop.dto.request.StreetPopSearchRequest;
import com.sangkwon.sangkwonplatform.streetpop.dto.response.StreetPopResponse;
import com.sangkwon.sangkwonplatform.streetpop.entity.StreetPop;
import com.sangkwon.sangkwonplatform.streetpop.repository.StreetPopRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreetPopServiceTest {

    @Mock
    StreetPopRepository streetPopRepository;

    @InjectMocks
    StreetPopService streetPopService;

    @Test
    void 분기_상권으로_조회해서_DTO로_변환한다() {
        StreetPop s = mock(StreetPop.class);
        when(s.getTrdarCd()).thenReturn("3110001");
        when(s.getTotFlpopCo()).thenReturn(1500L);
        when(streetPopRepository.search("20242", "3110001")).thenReturn(List.of(s));

        List<StreetPopResponse> result = streetPopService.getStreetPops(
                new StreetPopSearchRequest("20242", "3110001"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trdarCd()).isEqualTo("3110001");
        assertThat(result.get(0).totFlpopCo()).isEqualTo(1500L);
        verify(streetPopRepository).search("20242", "3110001");
    }

    @Test
    void 필터가_없으면_null로_전체_조회한다() {
        when(streetPopRepository.search(null, null)).thenReturn(List.of());

        List<StreetPopResponse> result = streetPopService.getStreetPops(
                new StreetPopSearchRequest(null, null));

        assertThat(result).isEmpty();
        verify(streetPopRepository).search(null, null);
    }
}
