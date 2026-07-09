package com.sangkwon.sangkwonplatform.admin.support.service;

import com.sangkwon.sangkwonplatform.admin.support.dto.response.AdminSupportCardResponse;
import com.sangkwon.sangkwonplatform.admin.support.dto.response.AdminSupportCountsResponse;
import com.sangkwon.sangkwonplatform.admin.support.dto.response.AdminSupportPageResponse;
import com.sangkwon.sangkwonplatform.support.entity.SupportProgram;
import com.sangkwon.sangkwonplatform.support.entity.SupportProgramId;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository.AdminSupportListRow;
import com.sangkwon.sangkwonplatform.support.service.SupportProgramTypeTab;
import com.sangkwon.sangkwonplatform.support.service.SupportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminSupportService {

    private static final int MAX_SIZE = 100;
    private static final List<String> NO_TYPE = List.of("__none__");

    private final SupportProgramRepository programRepository;

    @Transactional(readOnly = true)
    public AdminSupportPageResponse search(String visibility, String source, String type, String keyword,
                                           int page, int size) {
        LocalDate today = LocalDate.now();
        String vis = oneOf(visibility, "Y", "N");
        String src = oneOf(source, "BIZINFO", "KSTARTUP");
        String qLike = like(keyword);

        SupportProgramTypeTab tab = SupportProgramTypeTab.fromCode(type);
        int typeMode = tab == null ? 0 : (tab == SupportProgramTypeTab.ETC ? 2 : 1);
        List<String> typeRaws = tab == null ? NO_TYPE
                : (tab == SupportProgramTypeTab.ETC ? SupportProgramTypeTab.namedRawValues() : tab.rawValues());

        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);

        Page<AdminSupportListRow> rows = programRepository.adminSearch(vis, src, typeMode, typeRaws, qLike, pageable);
        List<AdminSupportCardResponse> content = rows.getContent().stream()
                .map(r -> toCard(r, today))
                .toList();
        return new AdminSupportPageResponse(content, rows.getNumber(), rows.getSize(),
                rows.getTotalElements(), rows.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminSupportCountsResponse counts() {
        return AdminSupportCountsResponse.from(programRepository.adminCounts());
    }

    public void setVisibility(String sourceCd, String programId, boolean visible) {
        SupportProgram program = programRepository.findById(new SupportProgramId(sourceCd, programId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지원사업을 찾을 수 없습니다"));
        program.updateVisible(visible);
    }

    private AdminSupportCardResponse toCard(AdminSupportListRow r, LocalDate today) {
        SupportProgramTypeTab tab = SupportProgramTypeTab.of(r.getProgramType());
        LocalDate bgn = toDate(r.getApplyBgngDe());
        LocalDate end = toDate(r.getApplyEndDe());
        return new AdminSupportCardResponse(
                r.getSourceCd(), r.getProgramId(), r.getTitle(), tab.label(), r.getRegion(),
                bgn, end, r.getApplyPeriodRaw(),
                SupportStatus.of(bgn, end, r.getApplyPeriodRaw(), today),
                SupportStatus.dday(end, today),
                "Y".equals(r.getIsVisible()), r.getDetailUrl());
    }

    private static LocalDate toDate(LocalDateTime dt) {
        return dt == null ? null : dt.toLocalDate();
    }

    private static String oneOf(String value, String... allowed) {
        if (value == null) {
            return null;
        }
        for (String a : allowed) {
            if (a.equals(value)) {
                return value;
            }
        }
        return null;
    }

    private static String like(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase() + "%";
    }
}
