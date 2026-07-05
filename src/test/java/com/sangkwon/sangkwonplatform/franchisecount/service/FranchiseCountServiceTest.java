package com.sangkwon.sangkwonplatform.franchisecount.service;

import com.sangkwon.sangkwonplatform.franchisecount.dto.request.FranchiseCountSearchRequest;
import com.sangkwon.sangkwonplatform.franchisecount.dto.response.FranchiseCountResponse;
import com.sangkwon.sangkwonplatform.franchisecount.entity.FranchiseCount;
import com.sangkwon.sangkwonplatform.franchisecount.repository.FranchiseCountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FranchiseCountServiceTest {

    @Mock
    FranchiseCountRepository franchiseCountRepository;

    @InjectMocks
    FranchiseCountService franchiseCountService;

    @Test
    void 연도_지역_업종으로_조회해서_DTO로_변환한다() {
        FranchiseCount fc = mock(FranchiseCount.class);
        when(fc.getAreaCd()).thenReturn("11110");
        when(fc.getIndutyNm()).thenReturn("편의점");
        when(fc.getFrcCo()).thenReturn(120L);
        when(fc.getFrcRt()).thenReturn(new BigDecimal("12.3400"));
        when(franchiseCountRepository.search(2024, "11110", "편의점"))
                .thenReturn(List.of(fc));

        List<FranchiseCountResponse> result = franchiseCountService.getFranchiseCounts(
                new FranchiseCountSearchRequest(2024, "11110", "편의점"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).areaCd()).isEqualTo("11110");
        assertThat(result.get(0).indutyNm()).isEqualTo("편의점");
        assertThat(result.get(0).frcCo()).isEqualTo(120L);
        assertThat(result.get(0).frcRt()).isEqualByComparingTo("12.3400");
        verify(franchiseCountRepository).search(2024, "11110", "편의점");
    }

    @Test
    void 필터가_없으면_null로_전체_조회한다() {
        when(franchiseCountRepository.search(null, null, null)).thenReturn(List.of());

        List<FranchiseCountResponse> result = franchiseCountService.getFranchiseCounts(
                new FranchiseCountSearchRequest(null, null, null));

        assertThat(result).isEmpty();
        verify(franchiseCountRepository).search(null, null, null);
    }
}
