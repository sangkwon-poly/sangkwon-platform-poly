package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.SalesSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.SalesResponse;
import com.sangkwon.sangkwonplatform.map.entity.Sales;
import com.sangkwon.sangkwonplatform.map.repository.SalesRepository;
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
class SalesServiceTest {

    @Mock
    SalesRepository salesRepository;

    @InjectMocks
    SalesService salesService;

    @Test
    void 분기_상권_업종으로_조회해서_DTO로_변환한다() {
        Sales s = mock(Sales.class);
        when(s.getTrdarCd()).thenReturn("3110001");
        when(s.getThsmonSelngAmt()).thenReturn(50000000L);
        when(salesRepository.search("20242", "3110001", "CS100001")).thenReturn(List.of(s));

        List<SalesResponse> result = salesService.getSales(
                new SalesSearchRequest("20242", "3110001", "CS100001"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trdarCd()).isEqualTo("3110001");
        assertThat(result.get(0).thsmonSelngAmt()).isEqualTo(50000000L);
        verify(salesRepository).search("20242", "3110001", "CS100001");
    }

    @Test
    void 필터가_없으면_null로_전체_조회한다() {
        when(salesRepository.search(null, null, null)).thenReturn(List.of());

        List<SalesResponse> result = salesService.getSales(
                new SalesSearchRequest(null, null, null));

        assertThat(result).isEmpty();
        verify(salesRepository).search(null, null, null);
    }
}
