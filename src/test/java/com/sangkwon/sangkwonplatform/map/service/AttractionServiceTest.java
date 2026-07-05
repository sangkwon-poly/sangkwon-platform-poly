package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.AttractionSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.AttractionResponse;
import com.sangkwon.sangkwonplatform.map.entity.Attraction;
import com.sangkwon.sangkwonplatform.map.repository.AttractionRepository;
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
class AttractionServiceTest {

    @Mock
    AttractionRepository attractionRepository;

    @InjectMocks
    AttractionService attractionService;

    @Test
    void 분기_상권으로_조회해서_DTO로_변환한다() {
        Attraction a = mock(Attraction.class);
        when(a.getTrdarCd()).thenReturn("3110001");
        when(a.getSubwayStatnCo()).thenReturn(2L);
        when(attractionRepository.search("20242", "3110001")).thenReturn(List.of(a));

        List<AttractionResponse> result = attractionService.getAttractions(
                new AttractionSearchRequest("20242", "3110001"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trdarCd()).isEqualTo("3110001");
        assertThat(result.get(0).subwayStatnCo()).isEqualTo(2L);
        verify(attractionRepository).search("20242", "3110001");
    }

    @Test
    void 필터가_없으면_null로_전체_조회한다() {
        when(attractionRepository.search(null, null)).thenReturn(List.of());

        List<AttractionResponse> result = attractionService.getAttractions(
                new AttractionSearchRequest(null, null));

        assertThat(result).isEmpty();
        verify(attractionRepository).search(null, null);
    }
}
