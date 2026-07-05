package com.sangkwon.sangkwonplatform.residentpop.service;

import com.sangkwon.sangkwonplatform.residentpop.dto.request.ResidentPopSearchRequest;
import com.sangkwon.sangkwonplatform.residentpop.dto.response.ResidentPopResponse;
import com.sangkwon.sangkwonplatform.residentpop.entity.ResidentPop;
import com.sangkwon.sangkwonplatform.residentpop.repository.ResidentPopRepository;
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
class ResidentPopServiceTest {

    @Mock
    ResidentPopRepository residentPopRepository;

    @InjectMocks
    ResidentPopService residentPopService;

    @Test
    void 분기_상권으로_조회해서_DTO로_변환한다() {
        ResidentPop r = mock(ResidentPop.class);
        when(r.getTrdarCd()).thenReturn("3110001");
        when(r.getTotRepopCo()).thenReturn(1500L);
        when(residentPopRepository.search("20242", "3110001")).thenReturn(List.of(r));

        List<ResidentPopResponse> result = residentPopService.getResidentPops(
                new ResidentPopSearchRequest("20242", "3110001"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trdarCd()).isEqualTo("3110001");
        assertThat(result.get(0).totRepopCo()).isEqualTo(1500L);
        verify(residentPopRepository).search("20242", "3110001");
    }

    @Test
    void 필터가_없으면_null로_전체_조회한다() {
        when(residentPopRepository.search(null, null)).thenReturn(List.of());

        List<ResidentPopResponse> result = residentPopService.getResidentPops(
                new ResidentPopSearchRequest(null, null));

        assertThat(result).isEmpty();
        verify(residentPopRepository).search(null, null);
    }
}
