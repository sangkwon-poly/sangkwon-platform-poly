package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.StoreStatSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.StoreStatResponse;
import com.sangkwon.sangkwonplatform.map.entity.StoreStat;
import com.sangkwon.sangkwonplatform.map.repository.StoreStatRepository;
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
class StoreStatServiceTest {

    @Mock
    StoreStatRepository storeStatRepository;

    @InjectMocks
    StoreStatService storeStatService;

    @Test
    void 분기_상권_업종으로_조회해서_DTO로_변환한다() {
        StoreStat s = mock(StoreStat.class);
        when(s.getTrdarCd()).thenReturn("3110001");
        when(s.getIndutyCd()).thenReturn("CS100001");
        when(s.getStorCo()).thenReturn(12L);
        when(storeStatRepository.search("20242", "3110001", "CS100001")).thenReturn(List.of(s));

        List<StoreStatResponse> result = storeStatService.getStoreStats(
                new StoreStatSearchRequest("20242", "3110001", "CS100001"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trdarCd()).isEqualTo("3110001");
        assertThat(result.get(0).indutyCd()).isEqualTo("CS100001");
        assertThat(result.get(0).storCo()).isEqualTo(12L);
        verify(storeStatRepository).search("20242", "3110001", "CS100001");
    }

    @Test
    void 필터가_없으면_null로_전체_조회한다() {
        when(storeStatRepository.search(null, null, null)).thenReturn(List.of());

        List<StoreStatResponse> result = storeStatService.getStoreStats(
                new StoreStatSearchRequest(null, null, null));

        assertThat(result).isEmpty();
        verify(storeStatRepository).search(null, null, null);
    }
}
