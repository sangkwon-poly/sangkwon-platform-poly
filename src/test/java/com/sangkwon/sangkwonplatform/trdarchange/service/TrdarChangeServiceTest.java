package com.sangkwon.sangkwonplatform.trdarchange.service;

import com.sangkwon.sangkwonplatform.trdarchange.dto.request.TrdarChangeSearchRequest;
import com.sangkwon.sangkwonplatform.trdarchange.dto.response.TrdarChangeResponse;
import com.sangkwon.sangkwonplatform.trdarchange.entity.TrdarChange;
import com.sangkwon.sangkwonplatform.trdarchange.repository.TrdarChangeRepository;
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
class TrdarChangeServiceTest {

    @Mock
    TrdarChangeRepository trdarChangeRepository;

    @InjectMocks
    TrdarChangeService trdarChangeService;

    @Test
    void 분기_상권으로_조회해서_DTO로_변환한다() {
        TrdarChange t = mock(TrdarChange.class);
        when(t.getTrdarCd()).thenReturn("3110001");
        when(t.getTrdarChngeIx()).thenReturn("HH");
        when(trdarChangeRepository.search("20242", "3110001")).thenReturn(List.of(t));

        List<TrdarChangeResponse> result = trdarChangeService.getTrdarChanges(
                new TrdarChangeSearchRequest("20242", "3110001"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trdarCd()).isEqualTo("3110001");
        assertThat(result.get(0).trdarChngeIx()).isEqualTo("HH");
        verify(trdarChangeRepository).search("20242", "3110001");
    }

    @Test
    void 필터가_없으면_null로_전체_조회한다() {
        when(trdarChangeRepository.search(null, null)).thenReturn(List.of());

        List<TrdarChangeResponse> result = trdarChangeService.getTrdarChanges(
                new TrdarChangeSearchRequest(null, null));

        assertThat(result).isEmpty();
        verify(trdarChangeRepository).search(null, null);
    }
}
