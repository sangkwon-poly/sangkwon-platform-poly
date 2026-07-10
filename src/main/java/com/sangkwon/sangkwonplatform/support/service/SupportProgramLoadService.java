package com.sangkwon.sangkwonplatform.support.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// 창업지원사업(기업마당 + K-Startup) 적재를 앱에서 실행한다. 파이썬 06_load_support_program.py 포팅.
// 서울 필터로 전 페이지를 수집해 SUPPORT_PROGRAM(+K-Startup 상세)에 (SOURCE_CD, PROGRAM_ID) MERGE upsert.
// 반복 실행해도 안전하고, 관리자 노출값(IS_VISIBLE)은 배치가 건드리지 않는다(MERGE UPDATE에서 제외).
// DML은 NamedParameterJdbcTemplate으로 바인딩한다(문자열 리터럴 콜론 오인/널 타입 추론 문제 회피).
@Service
public class SupportProgramLoadService {

    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGE = 60; // 폭주 방지 안전장치
    private static final Pattern YMD8 = Pattern.compile("\\d{8}");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final NamedParameterJdbcTemplate jdbc;

    @Value("${bizinfo.service-key:}")
    private String bizinfoKey;

    @Value("${kstartup.service-key:}")
    private String kstartupKey;

    public SupportProgramLoadService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long load() {
        List<Program> programs = new ArrayList<>();
        List<Detail> details = new ArrayList<>();
        fetchBizinfo(programs);
        fetchKstartup(programs, details);

        LocalDateTime now = LocalDateTime.now();
        Set<String> savedKstartupIds = new HashSet<>();
        long saved = 0;
        for (Program p : programs) {
            if (p.title == null) {
                continue; // TITLE NOT NULL
            }
            upsertProgram(p, now);
            if ("KSTARTUP".equals(p.sourceCd)) {
                savedKstartupIds.add(p.programId);
            }
            saved++;
        }
        for (Detail d : details) {
            // 제목이 없어 마스터를 건너뛴 공고의 상세는 넣지 않는다(FK 위반 나면 배치 전체가 롤백된다)
            if (savedKstartupIds.contains(d.programId)) {
                upsertDetail(d, now);
            }
        }
        return saved;
    }

    // ── 수집 ──────────────────────────────────────────────
    private void fetchBizinfo(List<Program> out) {
        for (int page = 1; page <= MAX_PAGE; page++) {
            String url = UriComponentsBuilder.fromUriString("https://www.bizinfo.go.kr/uss/rss/bizinfoApi.do")
                    .queryParam("crtfcKey", bizinfoKey)
                    .queryParam("dataType", "json")
                    .queryParam("searchLclasId", "06")
                    .queryParam("hashtags", "서울")
                    .queryParam("pageUnit", PAGE_SIZE)
                    .queryParam("pageIndex", page)
                    .encode().toUriString();

            JsonNode items = getJson(url).path("jsonArray");
            if (!items.isArray() || items.isEmpty()) {
                break;
            }
            for (JsonNode it : items) {
                Program p = new Program();
                p.programId = text(it, "pblancId");
                p.sourceCd = "BIZINFO";
                p.title = text(it, "pblancNm");
                p.programType = text(it, "pldirSportRealmLclasCodeNm");
                p.target = text(it, "trgetNm");
                p.description = stripHtml(text(it, "bsnsSumryCn"));
                LocalDate[] be = parsePeriod(text(it, "reqstBeginEndDe"));
                p.applyBgngDe = be[0];
                p.applyEndDe = be[1];
                p.applyPeriodRaw = text(it, "reqstBeginEndDe");
                p.detailUrl = text(it, "pblancUrl");
                p.sourceRegDt = parseRegDt(text(it, "creatPnttm"));
                out.add(p);
            }
            if (items.size() < PAGE_SIZE) {
                break;
            }
        }
    }

    private void fetchKstartup(List<Program> outP, List<Detail> outD) {
        for (int page = 1; page <= MAX_PAGE; page++) {
            String url = UriComponentsBuilder.fromUriString(
                            "https://apis.data.go.kr/B552735/kisedKstartupService01/getAnnouncementInformation01")
                    .queryParam("ServiceKey", kstartupKey)
                    .queryParam("page", page)
                    .queryParam("perPage", PAGE_SIZE)
                    .queryParam("returnType", "json")
                    .queryParam("supt_regin", "서울특별시")
                    .encode().toUriString();

            JsonNode root = getJson(url);
            JsonNode items = root.path("data");
            if (!items.isArray()) {
                items = root.path("items");
            }
            if (!items.isArray() || items.isEmpty()) {
                break;
            }
            for (JsonNode it : items) {
                String pid = text(it, "pbanc_sn");

                Program p = new Program();
                p.programId = pid;
                p.sourceCd = "KSTARTUP";
                p.title = text(it, "biz_pbanc_nm");
                p.programType = text(it, "supt_biz_clsfc");
                p.target = joinSlash(text(it, "aply_trgt"), text(it, "biz_enyy"), text(it, "biz_trgt_age"));
                p.region = text(it, "supt_regin");
                p.description = text(it, "pbanc_ctnt");
                p.applyBgngDe = parseYmd(text(it, "pbanc_rcpt_bgng_dt"));
                p.applyEndDe = parseYmd(text(it, "pbanc_rcpt_end_dt"));
                p.recruitYn = normalizeYn(text(it, "rcrt_prgs_yn"));
                p.contact = text(it, "prch_cnpl_no");
                p.detailUrl = text(it, "detl_pg_url");
                outP.add(p);

                Detail d = new Detail();
                d.programId = pid;
                d.aplyMthdVst = text(it, "aply_mthd_vst_rcpt_istc");
                d.aplyMthdPssr = text(it, "aply_mthd_pssr_rcpt_istc");
                d.aplyMthdFax = text(it, "aply_mthd_fax_rcpt_istc");
                d.aplyMthdEml = text(it, "aply_mthd_eml_rcpt_istc");
                d.aplyMthdOnli = text(it, "aply_mthd_onli_rcpt_istc");
                d.aplyMthdEtc = text(it, "aply_mthd_etc_istc");
                d.aplyExclTrgtCtnt = text(it, "aply_excl_trgt_ctnt");
                d.bizEnyy = text(it, "biz_enyy");
                d.bizTrgtAge = text(it, "biz_trgt_age");
                d.prfnMatr = text(it, "prfn_matr");
                d.sprvInst = text(it, "sprv_inst");
                d.pbancNtrpNm = text(it, "pbanc_ntrp_nm");
                d.bizGdncUrl = text(it, "biz_gdnc_url");
                d.intgPbancYn = normalizeYn(text(it, "intg_pbanc_yn"));
                outD.add(d);
            }
            if (items.size() < PAGE_SIZE) {
                break;
            }
        }
    }

    // ── 저장(MERGE upsert) ────────────────────────────────
    private void upsertProgram(Program p, LocalDateTime now) {
        String sql = "MERGE INTO SUPPORT_PROGRAM tgt "
                + "USING (SELECT :programId PROGRAM_ID, :sourceCd SOURCE_CD FROM dual) src "
                + "ON (tgt.PROGRAM_ID = src.PROGRAM_ID AND tgt.SOURCE_CD = src.SOURCE_CD) "
                + "WHEN MATCHED THEN UPDATE SET "
                + "TITLE=:title, PROGRAM_TYPE=:programType, TARGET=:target, REGION=:region, DESCRIPTION=:description, "
                + "APPLY_BGNG_DE=:bgn, APPLY_END_DE=:end, APPLY_PERIOD_RAW=:periodRaw, "
                + "RECRUIT_YN=:recruitYn, CONTACT=:contact, DETAIL_URL=:detailUrl, SOURCE_REG_DT=:srcRegDt, UPDATED_AT=:now "
                + "WHEN NOT MATCHED THEN INSERT ("
                + "PROGRAM_ID, SOURCE_CD, TITLE, PROGRAM_TYPE, TARGET, REGION, DESCRIPTION, "
                + "APPLY_BGNG_DE, APPLY_END_DE, APPLY_PERIOD_RAW, RECRUIT_YN, CONTACT, DETAIL_URL, SOURCE_REG_DT, CREATED_AT, UPDATED_AT) "
                + "VALUES (:programId, :sourceCd, :title, :programType, :target, :region, :description, "
                + ":bgn, :end, :periodRaw, :recruitYn, :contact, :detailUrl, :srcRegDt, :now, :now)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("programId", p.programId)
                .addValue("sourceCd", p.sourceCd)
                .addValue("title", clip(p.title, 1000))
                .addValue("programType", clip(p.programType, 100))
                .addValue("target", clip(p.target, 1000))
                .addValue("region", clip(p.region, 100))
                .addValue("description", p.description)
                .addValue("bgn", p.applyBgngDe, Types.DATE)
                .addValue("end", p.applyEndDe, Types.DATE)
                .addValue("periodRaw", clip(p.applyPeriodRaw, 200))
                .addValue("recruitYn", p.recruitYn)
                .addValue("contact", clip(p.contact, 500))
                .addValue("detailUrl", clip(p.detailUrl, 1000))
                .addValue("srcRegDt", p.sourceRegDt, Types.TIMESTAMP)
                .addValue("now", now, Types.TIMESTAMP);
        jdbc.update(sql, params);
    }

    private void upsertDetail(Detail d, LocalDateTime now) {
        String sql = "MERGE INTO SUPPORT_PROGRAM_KSTARTUP_DETAIL tgt "
                + "USING (SELECT :programId PROGRAM_ID, 'KSTARTUP' SOURCE_CD FROM dual) src "
                + "ON (tgt.PROGRAM_ID = src.PROGRAM_ID AND tgt.SOURCE_CD = src.SOURCE_CD) "
                + "WHEN MATCHED THEN UPDATE SET "
                + "APLY_MTHD_VST=:vst, APLY_MTHD_PSSR=:pssr, APLY_MTHD_FAX=:fax, APLY_MTHD_EML=:eml, "
                + "APLY_MTHD_ONLI=:onli, APLY_MTHD_ETC=:etc, APLY_EXCL_TRGT_CTNT=:excl, BIZ_ENYY=:enyy, "
                + "BIZ_TRGT_AGE=:age, PRFN_MATR=:prfn, SPRV_INST=:sprv, PBANC_NTRP_NM=:ntrp, "
                + "BIZ_GDNC_URL=:gdnc, INTG_PBANC_YN=:intg, UPDATED_AT=:now "
                + "WHEN NOT MATCHED THEN INSERT ("
                + "PROGRAM_ID, SOURCE_CD, APLY_MTHD_VST, APLY_MTHD_PSSR, APLY_MTHD_FAX, APLY_MTHD_EML, "
                + "APLY_MTHD_ONLI, APLY_MTHD_ETC, APLY_EXCL_TRGT_CTNT, BIZ_ENYY, BIZ_TRGT_AGE, PRFN_MATR, "
                + "SPRV_INST, PBANC_NTRP_NM, BIZ_GDNC_URL, INTG_PBANC_YN, CREATED_AT, UPDATED_AT) "
                + "VALUES (:programId, 'KSTARTUP', :vst, :pssr, :fax, :eml, :onli, :etc, :excl, :enyy, "
                + ":age, :prfn, :sprv, :ntrp, :gdnc, :intg, :now, :now)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("programId", d.programId)
                .addValue("vst", clip(d.aplyMthdVst, 4000))
                .addValue("pssr", clip(d.aplyMthdPssr, 4000))
                .addValue("fax", clip(d.aplyMthdFax, 4000))
                .addValue("eml", clip(d.aplyMthdEml, 4000))
                .addValue("onli", clip(d.aplyMthdOnli, 4000))
                .addValue("etc", clip(d.aplyMthdEtc, 4000))
                .addValue("excl", d.aplyExclTrgtCtnt)
                .addValue("enyy", clip(d.bizEnyy, 1000))
                .addValue("age", clip(d.bizTrgtAge, 1000))
                .addValue("prfn", d.prfnMatr)
                .addValue("sprv", clip(d.sprvInst, 200))
                .addValue("ntrp", clip(d.pbancNtrpNm, 1000))
                .addValue("gdnc", clip(d.bizGdncUrl, 2000))
                .addValue("intg", d.intgPbancYn)
                .addValue("now", now, Types.TIMESTAMP);
        jdbc.update(sql, params);
    }

    // ── 유틸 ──────────────────────────────────────────────
    private JsonNode getJson(String url) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String body = rest.getForObject(URI.create(url), String.class);
                return mapper.readTree(body);
            } catch (RuntimeException e) {
                last = e;
                sleep(2000);
            }
        }
        throw (last != null) ? last : new IllegalStateException("응답 없음: " + url);
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private String stripHtml(String s) {
        if (s == null) {
            return null;
        }
        String out = s.replaceAll("<[^>]*>", "").trim();
        return out.isEmpty() ? null : out;
    }

    private String joinSlash(String... parts) {
        List<String> nonNull = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                nonNull.add(p);
            }
        }
        return nonNull.isEmpty() ? null : String.join(" / ", nonNull);
    }

    // "2026-07-01 ~ 2026-07-31" -> [LocalDate, LocalDate]
    private LocalDate[] parsePeriod(String raw) {
        if (raw == null || !raw.contains("~")) {
            return new LocalDate[]{null, null};
        }
        String[] parts = raw.split("~");
        if (parts.length != 2) {
            return new LocalDate[]{null, null};
        }
        return new LocalDate[]{parseDash(parts[0].trim()), parseDash(parts[1].trim())};
    }

    private LocalDate parseDash(String s) {
        try {
            return LocalDate.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // "20260703" -> LocalDate
    private LocalDate parseYmd(String s) {
        if (s == null || !YMD8.matcher(s).matches()) {
            return null;
        }
        try {
            return LocalDate.parse(s, YMD);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private LocalDateTime parseRegDt(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, TS);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String normalizeYn(String s) {
        if (s == null) {
            return null;
        }
        if (s.equalsIgnoreCase("Y")) {
            return "Y";
        }
        if (s.equalsIgnoreCase("N")) {
            return "N";
        }
        return null;
    }

    // VARCHAR 길이 초과 방지(원본이 예상보다 길 때 잘라 저장)
    private String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class Program {
        String programId, sourceCd, title, programType, target, region, description;
        LocalDate applyBgngDe, applyEndDe;
        String applyPeriodRaw, recruitYn, contact, detailUrl;
        LocalDateTime sourceRegDt;
    }

    private static class Detail {
        String programId, aplyMthdVst, aplyMthdPssr, aplyMthdFax, aplyMthdEml, aplyMthdOnli, aplyMthdEtc;
        String aplyExclTrgtCtnt, bizEnyy, bizTrgtAge, prfnMatr, sprvInst, pbancNtrpNm, bizGdncUrl, intgPbancYn;
    }
}
