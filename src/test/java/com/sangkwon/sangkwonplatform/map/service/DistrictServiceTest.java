package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.DistrictSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictGeoResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictSummaryResponse;
import com.sangkwon.sangkwonplatform.map.entity.Trdar;
import com.sangkwon.sangkwonplatform.map.repository.DistrictGeo;
import com.sangkwon.sangkwonplatform.map.repository.DistrictSummary;
import com.sangkwon.sangkwonplatform.map.repository.TrdarRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistrictServiceTest {

    @Mock
    TrdarRepository trdarRepository;

    @InjectMocks
    DistrictService districtService;

    @Test
    void 필터_조건으로_조회해서_DTO로_변환한다() {
        Trdar trdar = mock(Trdar.class);
        when(trdar.getTrdarCd()).thenReturn("3110001");
        when(trdar.getTrdarSeCd()).thenReturn("A");
        when(trdarRepository.search("11680", "A")).thenReturn(List.of(trdar));

        List<DistrictResponse> result =
                districtService.getDistricts(new DistrictSearchRequest("11680", "A"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trdarCd()).isEqualTo("3110001");
        assertThat(result.get(0).seCd()).isEqualTo("A");
        verify(trdarRepository).search("11680", "A");
    }

    @Test
    void 필터가_없으면_null로_전체_조회한다() {
        when(trdarRepository.search(null, null)).thenReturn(List.of());

        List<DistrictResponse> result =
                districtService.getDistricts(new DistrictSearchRequest(null, null));

        assertThat(result).isEmpty();
        verify(trdarRepository).search(null, null);
    }

    @Test
    void 경계_조회해서_GeoJSON_DTO로_변환한다() {
        DistrictGeo geo = mock(DistrictGeo.class);
        when(geo.getTrdarCd()).thenReturn("3110001");
        when(geo.getGeoJson()).thenReturn(null);   // CLOB 읽기는 앱 구동 검증에서 확인
        when(trdarRepository.searchGeo("11680", null, null)).thenReturn(List.of(geo));

        List<DistrictGeoResponse> result =
                districtService.getGeometries(new DistrictSearchRequest("11680", null), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trdarCd()).isEqualTo("3110001");
        verify(trdarRepository).searchGeo("11680", null, null);
    }

    @Test
    void 상권코드로_경계_하나만_조회한다() {
        DistrictGeo geo = mock(DistrictGeo.class);
        when(geo.getTrdarCd()).thenReturn("3110001");
        when(trdarRepository.searchGeo(null, null, "3110001")).thenReturn(List.of(geo));

        List<DistrictGeoResponse> result =
                districtService.getGeometries(new DistrictSearchRequest(null, null), "3110001");

        assertThat(result).hasSize(1);
        verify(trdarRepository).searchGeo(null, null, "3110001");
    }

    @Test
    void 단일_상권을_조회한다() {
        Trdar trdar = mock(Trdar.class);
        when(trdar.getTrdarCd()).thenReturn("3110001");
        when(trdarRepository.findById("3110001")).thenReturn(Optional.of(trdar));

        DistrictResponse result = districtService.getDistrict("3110001");

        assertThat(result.trdarCd()).isEqualTo("3110001");
    }

    @Test
    void 없는_상권이면_예외() {
        when(trdarRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> districtService.getDistrict("nope"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 상권_요약을_조회해서_DTO로_변환한다() {
        DistrictSummary s = mock(DistrictSummary.class);
        when(s.getTrdarCd()).thenReturn("3110001");
        when(s.getSalesAmt()).thenReturn(74_000_000_000L);
        when(s.getQuarter()).thenReturn("20261");
        when(trdarRepository.searchSummary(null, null, "강남")).thenReturn(List.of(s));

        List<DistrictSummaryResponse> result = districtService.getSummaries(null, null, "강남");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trdarCd()).isEqualTo("3110001");
        assertThat(result.get(0).salesAmt()).isEqualTo(74_000_000_000L);
        assertThat(result.get(0).quarter()).isEqualTo("20261");
        verify(trdarRepository).searchSummary(null, null, "강남");
    }
}
