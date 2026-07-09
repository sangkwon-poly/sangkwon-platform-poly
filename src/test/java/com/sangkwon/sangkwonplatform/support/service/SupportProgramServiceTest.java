package com.sangkwon.sangkwonplatform.support.service;

import com.sangkwon.sangkwonplatform.support.dto.request.SupportProgramSearchRequest;
import com.sangkwon.sangkwonplatform.support.dto.response.SupportProgramDetailResponse;
import com.sangkwon.sangkwonplatform.support.dto.response.SupportProgramPageResponse;
import com.sangkwon.sangkwonplatform.support.entity.SupportProgram;
import com.sangkwon.sangkwonplatform.support.entity.SupportProgramKstartupDetail;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramKstartupDetailRepository;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository.SupportProgramListRow;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository.TypeCountRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportProgramServiceTest {

    @Mock
    SupportProgramRepository programRepository;

    @Mock
    SupportProgramKstartupDetailRepository detailRepository;

    @InjectMocks
    SupportProgramService supportProgramService;

    private SupportProgramSearchRequest req(String founding, Boolean includeUnknown) {
        return new SupportProgramSearchRequest(null, null, null, null, null,
                founding, null, null, includeUnknown, null);
    }

    private void stubSearch(Page<SupportProgramListRow> page) {
        when(programRepository.search(any(), any(), any(), any(), anyInt(), any(), anyInt(), any(),
                any(), any(), anyInt(), any())).thenReturn(page);
    }

    private void stubTypeCounts(List<TypeCountRow> rows) {
        when(programRepository.typeCounts(any(), any(), any(), any(), anyInt(), any(), any(), any(), anyInt()))
                .thenReturn(rows);
    }

    private TypeCountRow typeCount(String rawType, long cnt) {
        TypeCountRow row = mock(TypeCountRow.class);
        when(row.getProgramType()).thenReturn(rawType);
        when(row.getCnt()).thenReturn(cnt);
        return row;
    }

    @Test
    void 마감일로_상태와_D데이를_계산한다() {
        LocalDate today = LocalDate.now();
        SupportProgramListRow soon = mock(SupportProgramListRow.class);
        when(soon.getApplyEndDe()).thenReturn(today.plusDays(2).atStartOfDay());
        SupportProgramListRow always = mock(SupportProgramListRow.class);
        when(always.getApplyEndDe()).thenReturn(null);
        when(always.getApplyPeriodRaw()).thenReturn("상시 접수");

        stubSearch(new PageImpl<>(List.of(soon, always), PageRequest.of(0, 20), 2));
        stubTypeCounts(List.of());

        SupportProgramPageResponse res = supportProgramService.search(req(null, null), 0, 20);

        assertThat(res.content()).hasSize(2);
        assertThat(res.content().get(0).status()).isEqualTo("CLOSING");
        assertThat(res.content().get(0).dday()).isEqualTo(2);
        assertThat(res.content().get(1).status()).isEqualTo("ALWAYS");
        assertThat(res.content().get(1).dday()).isNull();
    }

    @Test
    void 유형_원본값을_탭으로_합산한다() {
        stubSearch(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        stubTypeCounts(List.of(typeCount("정책자금", 3), typeCount("융자ㆍ보증", 2), typeCount("사업화", 5)));

        SupportProgramPageResponse res = supportProgramService.search(req(null, null), 0, 20);

        assertThat(res.typeCounts()).anySatisfy(t -> {
            assertThat(t.tab()).isEqualTo("ALL");
            assertThat(t.count()).isEqualTo(10);
        });
        assertThat(res.typeCounts()).anySatisfy(t -> {
            assertThat(t.tab()).isEqualTo("FUND");
            assertThat(t.count()).isEqualTo(5);
        });
        assertThat(res.typeCounts()).anySatisfy(t -> {
            assertThat(t.tab()).isEqualTo("STARTUP");
            assertThat(t.count()).isEqualTo(5);
        });
    }

    @Test
    void 상세필터_활성이면_제외된_기업마당_수를_센다() {
        stubSearch(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        stubTypeCounts(List.of());
        when(programRepository.countBizinfoExcludable(any(), any(), any(), anyInt(), any(), anyInt(), any()))
                .thenReturn(43L);

        SupportProgramPageResponse res = supportProgramService.search(req("Y3", null), 0, 20);

        assertThat(res.excludedByDetailFilter()).isEqualTo(43);
    }

    @Test
    void 정보없는공고포함이면_제외수를_세지_않는다() {
        stubSearch(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        stubTypeCounts(List.of());

        SupportProgramPageResponse res = supportProgramService.search(req("Y3", true), 0, 20);

        assertThat(res.excludedByDetailFilter()).isZero();
        verify(programRepository, never()).countBizinfoExcludable(any(), any(), any(), anyInt(), any(), anyInt(), any());
    }

    @Test
    void 상세_K_Startup은_전용섹션을_채운다() {
        SupportProgram program = mock(SupportProgram.class);
        when(program.getIsVisible()).thenReturn("Y");
        when(program.getSourceCd()).thenReturn("KSTARTUP");
        when(program.getProgramType()).thenReturn("사업화");
        when(programRepository.findById(any())).thenReturn(Optional.of(program));

        SupportProgramKstartupDetail detail = mock(SupportProgramKstartupDetail.class);
        when(detail.getBizEnyy()).thenReturn("예비창업자,3년미만");
        when(detail.getAplyMthdOnli()).thenReturn("온라인 접수");
        when(detailRepository.findById(any())).thenReturn(Optional.of(detail));

        SupportProgramDetailResponse res = supportProgramService.getDetail("KSTARTUP", "178030");

        assertThat(res.typeLabel()).isEqualTo("창업·사업화");
        assertThat(res.kstartup()).isNotNull();
        assertThat(res.kstartup().foundingPeriod()).isEqualTo("예비창업자,3년미만");
        assertThat(res.kstartup().applyMethods()).contains("온라인");
    }

    @Test
    void 상세_기업마당은_전용섹션이_없다() {
        SupportProgram program = mock(SupportProgram.class);
        when(program.getIsVisible()).thenReturn("Y");
        when(program.getSourceCd()).thenReturn("BIZINFO");
        when(programRepository.findById(any())).thenReturn(Optional.of(program));

        SupportProgramDetailResponse res = supportProgramService.getDetail("BIZINFO", "x");

        assertThat(res.kstartup()).isNull();
        verify(detailRepository, never()).findById(any());
    }

    @Test
    void 없거나_숨김이면_예외() {
        when(programRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supportProgramService.getDetail("KSTARTUP", "nope"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
