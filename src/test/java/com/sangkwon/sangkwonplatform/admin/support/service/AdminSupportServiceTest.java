package com.sangkwon.sangkwonplatform.admin.support.service;

import com.sangkwon.sangkwonplatform.admin.support.dto.response.AdminSupportCardResponse;
import com.sangkwon.sangkwonplatform.admin.support.dto.response.AdminSupportCountsResponse;
import com.sangkwon.sangkwonplatform.admin.support.dto.response.AdminSupportPageResponse;
import com.sangkwon.sangkwonplatform.support.entity.SupportProgram;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository.AdminSupportCounts;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository.AdminSupportListRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSupportServiceTest {

    @Mock
    SupportProgramRepository programRepository;

    @InjectMocks
    AdminSupportService adminSupportService;

    @Test
    void 목록을_카드로_매핑하고_노출여부와_유형라벨을_채운다() {
        AdminSupportListRow row = mock(AdminSupportListRow.class);
        when(row.getSourceCd()).thenReturn("KSTARTUP");
        when(row.getProgramId()).thenReturn("178030");
        when(row.getTitle()).thenReturn("샘플 공고");
        when(row.getProgramType()).thenReturn("사업화");
        when(row.getApplyEndDe()).thenReturn(LocalDate.now().plusDays(3).atStartOfDay());
        when(row.getIsVisible()).thenReturn("N");
        when(programRepository.adminSearch(any(), any(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        AdminSupportPageResponse res = adminSupportService.search(null, null, null, null, null, 0, 20);

        assertThat(res.content()).hasSize(1);
        AdminSupportCardResponse card = res.content().get(0);
        assertThat(card.typeLabel()).isEqualTo("창업·사업화");
        assertThat(card.visible()).isFalse();
        assertThat(card.status()).isEqualTo("CLOSING");
        assertThat(card.dday()).isEqualTo(3);
    }

    @Test
    void 요약_카운트를_매핑한다() {
        AdminSupportCounts counts = mock(AdminSupportCounts.class);
        when(counts.getTotal()).thenReturn(100L);
        when(counts.getVisible()).thenReturn(90L);
        when(counts.getHidden()).thenReturn(10L);
        when(counts.getBizinfo()).thenReturn(5L);
        when(counts.getKstartup()).thenReturn(95L);
        when(programRepository.adminCounts(any())).thenReturn(counts);

        AdminSupportCountsResponse res = adminSupportService.counts();

        assertThat(res.total()).isEqualTo(100);
        assertThat(res.hidden()).isEqualTo(10);
        assertThat(res.kstartup()).isEqualTo(95);
    }

    @Test
    void 노출여부를_전환한다() {
        SupportProgram program = mock(SupportProgram.class);
        when(programRepository.findById(any())).thenReturn(Optional.of(program));

        adminSupportService.setVisibility("KSTARTUP", "178030", false);

        verify(program).updateVisible(false);
    }

    @Test
    void 없는_공고면_예외() {
        when(programRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminSupportService.setVisibility("KSTARTUP", "nope", true))
                .isInstanceOf(ResponseStatusException.class);
    }
}
