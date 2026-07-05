package com.sangkwon.sangkwonplatform.rent.service;

import com.sangkwon.sangkwonplatform.rent.dto.request.RentSearchRequest;
import com.sangkwon.sangkwonplatform.rent.dto.response.RentResponse;
import com.sangkwon.sangkwonplatform.rent.entity.CommercialRent;
import com.sangkwon.sangkwonplatform.rent.repository.CommercialRentRepository;
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
class RentServiceTest {

    @Mock
    CommercialRentRepository commercialRentRepository;

    @InjectMocks
    RentService rentService;

    @Test
    void 지역_지표_유형_분기로_조회해서_DTO로_변환한다() {
        CommercialRent rent = mock(CommercialRent.class);
        when(rent.getRegionCd()).thenReturn("500002");
        when(rent.getMetricCd()).thenReturn("RENT");
        when(rent.getMetricValue()).thenReturn(new BigDecimal("28.34"));
        when(commercialRentRepository.search("500002", "RENT", "오피스", "20242"))
                .thenReturn(List.of(rent));

        List<RentResponse> result = rentService.getRents(
                new RentSearchRequest("500002", "RENT", "오피스", "20242"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).regionCd()).isEqualTo("500002");
        assertThat(result.get(0).metricCd()).isEqualTo("RENT");
        assertThat(result.get(0).metricValue()).isEqualByComparingTo("28.34");
        verify(commercialRentRepository).search("500002", "RENT", "오피스", "20242");
    }

    @Test
    void 필터가_없으면_null로_전체_조회한다() {
        when(commercialRentRepository.search(null, null, null, null)).thenReturn(List.of());

        List<RentResponse> result = rentService.getRents(
                new RentSearchRequest(null, null, null, null));

        assertThat(result).isEmpty();
        verify(commercialRentRepository).search(null, null, null, null);
    }
}
