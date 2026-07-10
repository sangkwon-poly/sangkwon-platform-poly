package com.sangkwon.sangkwonplatform.admin.ops;

// 사용량을 집계하는 외부 API 코드와 일일 한도. name()이 API_USAGE_LOG.API_NAME(체크 제약)과 일치해야 한다.
// Gemini는 리포트용과 뉴스 요약용 키가 달라 구글 쿼터도 따로 잡히므로 코드를 분리한다.
public enum ExternalApi {

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
