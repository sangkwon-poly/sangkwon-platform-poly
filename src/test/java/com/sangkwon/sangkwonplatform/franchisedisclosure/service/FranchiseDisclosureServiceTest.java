package com.sangkwon.sangkwonplatform.franchisedisclosure.service;

import com.sangkwon.sangkwonplatform.franchisedisclosure.dto.request.FranchiseDisclosureSearchRequest;
import com.sangkwon.sangkwonplatform.franchisedisclosure.dto.response.FranchiseDisclosureResponse;
import com.sangkwon.sangkwonplatform.franchisedisclosure.entity.FranchiseDisclosure;
import com.sangkwon.sangkwonplatform.franchisedisclosure.repository.FranchiseDisclosureRepository;
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
class FranchiseDisclosureServiceTest {

    @Mock
    FranchiseDisclosureRepository franchiseDisclosureRepository;

    @InjectMocks
    FranchiseDisclosureService franchiseDisclosureService;

    @Test
    void 브랜드명_법인명으로_조회해서_DTO로_변환한다() {
        FranchiseDisclosure disclosure = mock(FranchiseDisclosure.class);
        when(disclosure.getDisclosureSn()).thenReturn("2024-001");
        when(disclosure.getBrandNm()).thenReturn("메가커피");
        when(disclosure.getCorpNm()).thenReturn("주식회사 앤하우스");
        when(franchiseDisclosureRepository.search("메가", "주식회사 앤하우스"))
                .thenReturn(List.of(disclosure));

        List<FranchiseDisclosureResponse> result = franchiseDisclosureService.getFranchiseDisclosures(
                new FranchiseDisclosureSearchRequest("메가", "주식회사 앤하우스"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).disclosureSn()).isEqualTo("2024-001");
        assertThat(result.get(0).brandNm()).isEqualTo("메가커피");
        assertThat(result.get(0).corpNm()).isEqualTo("주식회사 앤하우스");
        verify(franchiseDisclosureRepository).search("메가", "주식회사 앤하우스");
    }

    @Test
    void 필터가_없으면_null로_전체_조회한다() {
        when(franchiseDisclosureRepository.search(null, null)).thenReturn(List.of());

        List<FranchiseDisclosureResponse> result = franchiseDisclosureService.getFranchiseDisclosures(
                new FranchiseDisclosureSearchRequest(null, null));

        assertThat(result).isEmpty();
        verify(franchiseDisclosureRepository).search(null, null);
    }
}
