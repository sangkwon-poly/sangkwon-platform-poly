package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.SalesSearchRequest;
import com.sangkwon.sangkwonplatform.map.entity.Sales;
import com.sangkwon.sangkwonplatform.map.repository.SalesRepository;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
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
    void 분기_상권_업종으로_조회해서_메타데이터와_함께_반환한다() {
        Sales s = mock(Sales.class);
        when(s.getTrdarCd()).thenReturn("3110001");
        when(s.getThsmonSelngAmt()).thenReturn(50000000L);
        when(salesRepository.countSearch("20242", "3110001", "CS100001")).thenReturn(1L);
        when(salesRepository.search(eq("20242"), eq("3110001"), eq("CS100001"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(s));

        var result = salesService.getSales(new SalesSearchRequest("20242", "3110001", "CS100001"));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).trdarCd()).isEqualTo("3110001");
        assertThat(result.truncated()).isFalse();
        verify(salesRepository).countSearch("20242", "3110001", "CS100001");
    }

    @Test
    void 필터가_없으면_400을_던진다() {
        assertThatThrownBy(() -> salesService.getSales(new SalesSearchRequest(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void 전체_건수가_상한보다_크면_truncated를_표시한다() {
        when(salesRepository.countSearch(null, "3110001", null)).thenReturn(6000L);
        when(salesRepository.search(eq(null), eq("3110001"), eq(null), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());

        var result = salesService.getSales(new SalesSearchRequest(null, "3110001", null));

        assertThat(result.truncated()).isTrue();
        assertThat(result.limit()).isEqualTo(SalesService.MAX_ROWS);
    }
}
