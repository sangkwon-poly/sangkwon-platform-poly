package com.sangkwon.sangkwonplatform.support.dto.request;

// 지원사업 목록 조회 필터. 값이 없으면(null) 해당 조건은 적용하지 않는다.
public record SupportProgramSearchRequest(
        String type,            // 유형 탭 코드 (SupportProgramTypeTab.name())
        String region,          // seoul / nation (그 외/전체는 null)
        String source,          // BIZINFO / KSTARTUP
        String target,          // 지원 대상 키워드 (TARGET 부분일치)
        String q,               // 공고명 검색
        String founding,        // 상세필터: 창업기간 코드
        String age,             // 상세필터: 연령 코드
        Boolean recruiting,     // true=모집중만(기본), false=마감 포함
        Boolean includeUnknown, // 상세필터 시 정보 없는(기업마당) 공고도 포함
        String sort             // deadline=마감임박순(기본)
) {
    // 상세필터(창업기간/연령)는 K-Startup 공고에만 있는 값이라 하나라도 켜지면 활성으로 본다
    public boolean detailFilterActive() {
        return founding != null || age != null;
    }
}
