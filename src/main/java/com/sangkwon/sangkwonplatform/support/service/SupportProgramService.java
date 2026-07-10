package com.sangkwon.sangkwonplatform.support.service;

import com.sangkwon.sangkwonplatform.support.dto.request.SupportProgramSearchRequest;
import com.sangkwon.sangkwonplatform.support.dto.response.SupportProgramCardResponse;
import com.sangkwon.sangkwonplatform.support.dto.response.SupportProgramDetailResponse;
import com.sangkwon.sangkwonplatform.support.dto.response.SupportProgramPageResponse;
import com.sangkwon.sangkwonplatform.support.entity.SupportProgram;
import com.sangkwon.sangkwonplatform.support.entity.SupportProgramId;
import com.sangkwon.sangkwonplatform.support.entity.SupportProgramKstartupDetail;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramKstartupDetailRepository;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository.SupportProgramListRow;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository.TypeCountRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportProgramService {

    // 유형 필터 미사용 시 IN 절이 비지 않도록 넣는 더미값
    private static final List<String> NO_TYPE = List.of("__none__");

    private final SupportProgramRepository programRepository;
    private final SupportProgramKstartupDetailRepository detailRepository;

    public SupportProgramPageResponse search(SupportProgramSearchRequest req, int page, int size) {
        LocalDate today = LocalDate.now();

        String source = oneOf(req.source(), "BIZINFO", "KSTARTUP");
        String region = oneOf(req.region(), "seoul", "nation");
        String targetLike = like(req.target());
        String qLike = like(req.q());
        int recruitingOnly = (req.recruiting() == null || req.recruiting()) ? 1 : 0;
        int includeUnknown = Boolean.TRUE.equals(req.includeUnknown()) ? 1 : 0;
        String foundingLike = foundingPattern(req.founding());
        String ageLike = agePattern(req.age());

        SupportProgramTypeTab tab = SupportProgramTypeTab.fromCode(req.type());
        int typeMode = tab == null ? 0 : (tab == SupportProgramTypeTab.ETC ? 2 : 1);
        List<String> typeRaws = tab == null ? NO_TYPE
                : (tab == SupportProgramTypeTab.ETC ? SupportProgramTypeTab.namedRawValues() : tab.rawValues());

        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);

        Page<SupportProgramListRow> rows = programRepository.search(source, region, targetLike, qLike,
                typeMode, typeRaws, recruitingOnly, today, foundingLike, ageLike, includeUnknown, pageable);

        List<SupportProgramCardResponse> content = rows.getContent().stream()
                .map(r -> toCard(r, today))
                .toList();

        List<SupportProgramPageResponse.TypeCount> typeCounts = buildTypeCounts(
                programRepository.typeCounts(source, region, targetLike, qLike,
                        recruitingOnly, today, foundingLike, ageLike, includeUnknown));

        int excluded = 0;
        if (req.detailFilterActive() && includeUnknown == 0 && !"KSTARTUP".equals(source)) {
            excluded = (int) programRepository.countBizinfoExcludable(region, targetLike, qLike,
                    typeMode, typeRaws, recruitingOnly, today);
        }

        return new SupportProgramPageResponse(content, rows.getNumber(), rows.getSize(),
                rows.getTotalElements(), rows.getTotalPages(), typeCounts, excluded);
    }

    public SupportProgramDetailResponse getDetail(String sourceCd, String programId) {
        return getDetail(sourceCd, programId, true);
    }

    // requireVisible=false면 숨김 공고도 조립한다(관리자 상세용).
    public SupportProgramDetailResponse getDetail(String sourceCd, String programId, boolean requireVisible) {
        SupportProgram p = programRepository.findById(new SupportProgramId(sourceCd, programId))
                .filter(sp -> !requireVisible || "Y".equals(sp.getIsVisible()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지원사업을 찾을 수 없습니다"));

        LocalDate today = LocalDate.now();
        SupportProgramTypeTab tab = SupportProgramTypeTab.of(p.getProgramType());

        SupportProgramDetailResponse.Kstartup kstartup = null;
        if ("KSTARTUP".equals(p.getSourceCd())) {
            kstartup = detailRepository.findById(new SupportProgramId(sourceCd, programId))
                    .map(SupportProgramService::toKstartup)
                    .orElse(null);
        }

        return new SupportProgramDetailResponse(
                p.getSourceCd(), p.getProgramId(), p.getTitle(), tab.name(), tab.label(),
                p.getRegion(), p.getTarget(), p.getDescription(),
                p.getApplyBgngDe(), p.getApplyEndDe(), p.getApplyPeriodRaw(),
                SupportStatus.of(p.getApplyBgngDe(), p.getApplyEndDe(), p.getApplyPeriodRaw(), p.getRecruitYn(), today),
                SupportStatus.dday(p.getApplyEndDe(), today), p.getContact(), p.getDetailUrl(),
                "Y".equals(p.getIsVisible()), kstartup);
    }

    private SupportProgramCardResponse toCard(SupportProgramListRow r, LocalDate today) {
        SupportProgramTypeTab tab = SupportProgramTypeTab.of(r.getProgramType());
        LocalDate bgn = toDate(r.getApplyBgngDe());
        LocalDate end = toDate(r.getApplyEndDe());
        return new SupportProgramCardResponse(
                r.getSourceCd(), r.getProgramId(), r.getTitle(), tab.name(), tab.label(),
                r.getOrg(), r.getRegion(), bgn, end, r.getApplyPeriodRaw(),
                SupportStatus.of(bgn, end, r.getApplyPeriodRaw(), r.getRecruitYn(), today),
                SupportStatus.dday(end, today), r.getDetailUrl());
    }

    private static LocalDate toDate(java.time.LocalDateTime dt) {
        return dt == null ? null : dt.toLocalDate();
    }

    private List<SupportProgramPageResponse.TypeCount> buildTypeCounts(List<TypeCountRow> rows) {
        Map<SupportProgramTypeTab, Long> byTab = new EnumMap<>(SupportProgramTypeTab.class);
        long total = 0;
        // 배지 카운트는 목록 필터(program_type IN)와 같은 exact 기준으로 센다. 카드 라벨은 of()로 폭넓게
        // 보여주므로, 비정규 구분자 값에선 라벨과 배지가 갈릴 수 있다(실데이터는 정규값이라 사실상 일치).
        for (TypeCountRow row : rows) {
            byTab.merge(SupportProgramTypeTab.fromRawValue(row.getProgramType()), row.getCnt(), Long::sum);
            total += row.getCnt();
        }
        List<SupportProgramPageResponse.TypeCount> out = new ArrayList<>();
        out.add(new SupportProgramPageResponse.TypeCount("ALL", "전체", total));
        for (SupportProgramTypeTab t : SupportProgramTypeTab.values()) {
            out.add(new SupportProgramPageResponse.TypeCount(t.name(), t.label(), byTab.getOrDefault(t, 0L)));
        }
        return out;
    }

    private static SupportProgramDetailResponse.Kstartup toKstartup(SupportProgramKstartupDetail d) {
        List<String> methods = new ArrayList<>();
        addMethod(methods, "온라인", d.getAplyMthdOnli());
        addMethod(methods, "방문", d.getAplyMthdVst());
        addMethod(methods, "우편", d.getAplyMthdPssr());
        addMethod(methods, "팩스", d.getAplyMthdFax());
        addMethod(methods, "이메일", d.getAplyMthdEml());
        addMethod(methods, "기타", d.getAplyMthdEtc());
        return new SupportProgramDetailResponse.Kstartup(
                d.getBizEnyy(), d.getBizTrgtAge(), methods,
                d.getAplyExclTrgtCtnt(), d.getPrfnMatr(),
                d.getSprvInst(), d.getPbancNtrpNm(), d.getBizGdncUrl());
    }

    private static void addMethod(List<String> methods, String label, String value) {
        if (value != null && !value.isBlank()) {
            methods.add(label);
        }
    }

    // 상세필터 코드 -> 원문 매칭 LIKE.
    // BIZ_ENYY 원문은 콤마 나열 형식(예: "예비창업자,1년미만,3년미만,...")이라 "N년미만"으로 부분일치한다.
    private String foundingPattern(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "PRELIMINARY" -> "%예비창업%";
            case "Y1" -> "%1년미만%";
            case "Y3" -> "%3년미만%";
            case "Y5" -> "%5년미만%";
            case "Y7" -> "%7년미만%";
            case "Y10" -> "%10년미만%";
            default -> null;
        };
    }

    // BIZ_TRGT_AGE 원문은 "만 20세 미만,만 20세 이상 ~ 만 39세 이하,만 40세 이상" 형식이라 구간별 특징 문구로 부분일치한다.
    private String agePattern(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "UNDER20" -> "%20세 미만%";
            case "A20_39" -> "%39세 이하%";
            case "OVER40" -> "%40세 이상%";
            default -> null;
        };
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
