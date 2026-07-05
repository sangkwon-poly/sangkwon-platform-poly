package com.sangkwon.sangkwonplatform.franchisebrand.service;

import com.sangkwon.sangkwonplatform.franchisebrand.dto.request.FranchiseBrandSearchRequest;
import com.sangkwon.sangkwonplatform.franchisebrand.dto.response.FranchiseBrandResponse;
import com.sangkwon.sangkwonplatform.franchisebrand.entity.FranchiseBrand;
import com.sangkwon.sangkwonplatform.franchisebrand.repository.FranchiseBrandRepository;
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
class FranchiseBrandServiceTest {

    @Mock
    FranchiseBrandRepository franchiseBrandRepository;

    @InjectMocks
    FranchiseBrandService franchiseBrandService;

    @Test
    void 브랜드명_업종으로_조회해서_DTO로_변환한다() {
        FranchiseBrand brand = mock(FranchiseBrand.class);
        when(brand.getBrandMgmtNo()).thenReturn("20240001");
        when(brand.getBrandNm()).thenReturn("메가커피");
        when(brand.getIndutyLclasNm()).thenReturn("외식");
        when(franchiseBrandRepository.search("메가", "외식"))
                .thenReturn(List.of(brand));

        List<FranchiseBrandResponse> result = franchiseBrandService.getFranchiseBrands(
                new FranchiseBrandSearchRequest("메가", "외식"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).brandMgmtNo()).isEqualTo("20240001");
        assertThat(result.get(0).brandNm()).isEqualTo("메가커피");
        assertThat(result.get(0).indutyLclasNm()).isEqualTo("외식");
        verify(franchiseBrandRepository).search("메가", "외식");
    }

    @Test
    void 필터가_없으면_null로_전체_조회한다() {
        when(franchiseBrandRepository.search(null, null)).thenReturn(List.of());

        List<FranchiseBrandResponse> result = franchiseBrandService.getFranchiseBrands(
                new FranchiseBrandSearchRequest(null, null));

        assertThat(result).isEmpty();
        verify(franchiseBrandRepository).search(null, null);
    }
}
