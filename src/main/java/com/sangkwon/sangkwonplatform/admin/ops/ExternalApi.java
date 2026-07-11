package com.sangkwon.sangkwonplatform.admin.ops;

// 사용량을 집계하는 외부 API 코드와 일일 한도. name()이 API_USAGE_LOG.API_NAME(체크 제약)과 일치해야 한다.
// Gemini는 리포트용과 뉴스 요약용 키가 달라 구글 쿼터도 따로 잡히므로 코드를 분리한다.
// SEOUL은 열린데이터광장 인증키 기본 한도(일 1,000회), FTC_FRANCHISE는 공공데이터포털 개발계정(일 10,000회) 문서 기준.
// REB_RONE과 KIPRIS는 공개 문서에 한도 수치가 없어 잠정값(일 10,000회 / 일 1,000회)을 적용했다.
public enum ExternalApi {

    FTC_FRANCHISE("공정위 가맹사업", 10000),
    KIPRIS("특허정보넷 KIPRIS", 1000),
    REB_RONE("부동산원 R-ONE", 10000),
    SEOUL("서울 열린데이터광장", 1000),
    GEMINI("AI 리포트 생성", 1000),
    GEMINI_NEWS("업계동향 뉴스 요약", 1000);

    private final String label;
    private final long dailyLimit;

    ExternalApi(String label, long dailyLimit) {
        this.label = label;
        this.dailyLimit = dailyLimit;
    }

    public String label() {
        return label;
    }

    public long dailyLimit() {
        return dailyLimit;
    }
}
