package com.sangkwon.sangkwonplatform.global.batch;

import java.util.Arrays;
import java.util.Optional;

// 적재 대상 데이터셋 레지스트리. 화면 카탈로그, 실테이블 집계, BATCH_JOB_LOG 기록이 이 한 곳을 본다.
// APP: 앱이 직접 실행할 수 있는 적재(외부 API 새로고침). OFFLINE: 파이썬 ETL로만 적재하고 화면은 상태만 비춘다.
// table/periodCol 은 실제 테이블 집계(COUNT, MAX(CREATED_AT), MAX(period))에 쓰는 신뢰된 상수다.
// sourceUrl 은 원본 데이터 출처(포털/API)로 이동하는 링크.
public enum Dataset {

    // 표시 순서: 서울 상권분석(기반 TRDAR 먼저) -> 부동산/프랜차이즈 -> 창업지원/뉴스. 출처별로 묶는다.
    TRDAR("상권 영역", Tier.OFFLINE, "TRDAR",
            null, PeriodKind.NONE,
            "상권 경계 폴리곤(서울시)",
            "https://data.seoul.go.kr"),
    SALES("상권 매출", Tier.APP, "SALES",
            "STDR_YYQU_CD", PeriodKind.QUARTER,
            "분기별 업종 매출(서울 열린데이터)",
            "https://data.seoul.go.kr"),
    STORE_STAT("점포 통계", Tier.APP, "STORE_STAT",
            "STDR_YYQU_CD", PeriodKind.QUARTER,
            "분기별 점포/개폐업(서울 열린데이터)",
            "https://data.seoul.go.kr"),
    TRDAR_CHANGE("상권 변화지표", Tier.APP, "TRDAR_CHANGE",
            "STDR_YYQU_CD", PeriodKind.QUARTER,
            "분기별 상권 변화 등급(서울 열린데이터)",
            "https://data.seoul.go.kr"),
    STREET_POP("유동인구", Tier.APP, "STREET_POP",
            "STDR_YYQU_CD", PeriodKind.QUARTER,
            "분기별 유동인구(서울 열린데이터)",
            "https://data.seoul.go.kr"),
    RESIDENT_POP("상주인구", Tier.APP, "RESIDENT_POP",
            "STDR_YYQU_CD", PeriodKind.QUARTER,
            "분기별 상주인구(서울 열린데이터)",
            "https://data.seoul.go.kr"),
    ATTRACTION("집객시설", Tier.APP, "ATTRACTION",
            "STDR_YYQU_CD", PeriodKind.QUARTER,
            "분기별 집객시설 수(서울 열린데이터)",
            "https://data.seoul.go.kr"),
    APT("아파트", Tier.APP, "APT",
            "STDR_YYQU_CD", PeriodKind.QUARTER,
            "분기별 아파트 단지/시세(서울 열린데이터)",
            "https://data.seoul.go.kr"),
    RENT("상업용부동산 임대", Tier.APP, "COMMERCIAL_RENT",
            "STDR_YYQU_CD", PeriodKind.QUARTER,
            "분기별 임대료/공실률(한국부동산원 R-ONE)",
            "https://www.reb.or.kr/r-one/"),
    FRANCHISE("프랜차이즈", Tier.APP, "FRANCHISE_COUNT",
            "BASE_YEAR", PeriodKind.YEAR,
            "공정위 브랜드/가맹점수/정보공개서",
            "https://franchise.ftc.go.kr"),
    SUPPORT_PROGRAM("창업지원사업", Tier.APP, "SUPPORT_PROGRAM",
            "SOURCE_REG_DT", PeriodKind.DATE,
            "기업마당 + K-Startup 공고(서울)",
            "https://www.bizinfo.go.kr"),
    INDUSTRY_NEWS("산업뉴스 인사이트", Tier.APP, "INDUSTRY_NEWS_INSIGHT",
            "YEAR_MONTH", PeriodKind.MONTH,
            "업종별 뉴스 요약(네이버 검색 + Gemini)",
            "https://developers.naver.com/docs/serviceapi/search/news/news.md");

    public enum Tier { APP, OFFLINE }

    // 데이터 최신 시점을 나타내는 기간 컬럼의 종류. 화면 표기 포맷과 주기 라벨을 결정한다.
    public enum PeriodKind { QUARTER, MONTH, YEAR, DATE, NONE }

    private final String label;
    private final Tier tier;
    private final String table;
    private final String periodCol;
    private final PeriodKind periodKind;
    private final String note;
    private final String sourceUrl;

    Dataset(String label, Tier tier, String table, String periodCol, PeriodKind periodKind,
            String note, String sourceUrl) {
        this.label = label;
        this.tier = tier;
        this.table = table;
        this.periodCol = periodCol;
        this.periodKind = periodKind;
        this.note = note;
        this.sourceUrl = sourceUrl;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public Tier tier() {
        return tier;
    }

    public String table() {
        return table;
    }

    public String periodCol() {
        return periodCol;
    }

    public PeriodKind periodKind() {
        return periodKind;
    }

    public String note() {
        return note;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public boolean appRunnable() {
        return tier == Tier.APP;
    }

    public String jobName() {
        return label + " 적재";
    }

    // 적재 주기 표시용
    public String cycleLabel() {
        return switch (periodKind) {
            case QUARTER -> "분기별";
            case MONTH -> "월별";
            case YEAR -> "연도별";
            case DATE -> "수시";
            case NONE -> "비정기";
        };
    }

    // 마지막 적재 후 이 일수를 넘기면 "오래됨"으로 본다(주기별로 새 데이터가 나올 만한 간격 기준).
    public int staleAfterDays() {
        return switch (periodKind) {
            case QUARTER -> 100;
            case MONTH -> 40;
            case YEAR -> 400;
            case DATE -> 30;
            case NONE -> 120;
        };
    }

    public static Optional<Dataset> byCode(String code) {
        return Arrays.stream(values()).filter(d -> d.name().equals(code)).findFirst();
    }
}
