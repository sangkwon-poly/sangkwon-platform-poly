package com.sangkwon.sangkwonplatform.support.service;

import java.util.Arrays;
import java.util.List;

// 원본 PROGRAM_TYPE(K-Startup 12종 + 기업마당 값)을 화면 유형 탭 10종으로 묶는다.
// 원본값은 구분자가 'ㆍ'(U+318D)이고 일부는 '&amp;'처럼 HTML 엔티티가 섞여 들어와,
// 매핑은 구분자/공백을 제거한 정규화 문자열로 비교한다. 탭별 원본값 목록은 목록 필터(IN)에 쓴다.
public enum SupportProgramTypeTab {

    FUND("자금·융자", "정책자금", "융자ㆍ보증"),
    RND("기술·R&D", "기술개발(R&D)", "기술개발(R&amp;D)"),
    HR("인력", "인력"),
    SALES("판로·수출·글로벌", "판로ㆍ해외진출", "글로벌"),
    STARTUP("창업·사업화", "사업화", "창업"),
    EDU("경영·멘토링·교육", "창업교육", "멘토링ㆍ컨설팅ㆍ교육"),
    FACILITY("시설·공간·보육", "시설ㆍ공간ㆍ보육"),
    EVENT("행사·네트워크", "행사ㆍ네트워크"),
    ETC("기타");

    private final String label;
    private final List<String> rawValues;

    SupportProgramTypeTab(String label, String... rawValues) {
        this.label = label;
        this.rawValues = List.of(rawValues);
    }

    public String label() {
        return label;
    }

    public List<String> rawValues() {
        return rawValues;
    }

    // 원본값 -> 탭. 구분자/공백을 지운 정규화로 비교해 'ㆍ'/'·'/'&amp;' 표기 차이를 흡수한다.
    public static SupportProgramTypeTab of(String rawType) {
        if (rawType == null) {
            return ETC;
        }
        String n = rawType.replace("&amp;", "&").replaceAll("[\\sㆍ·・]", "");
        if (n.contains("기술개발")) {
            return RND;
        }
        return switch (n) {
            case "정책자금", "융자보증" -> FUND;
            case "인력" -> HR;
            case "판로해외진출", "글로벌" -> SALES;
            case "사업화", "창업" -> STARTUP;
            case "창업교육", "멘토링컨설팅교육" -> EDU;
            case "시설공간보육" -> FACILITY;
            case "행사네트워크" -> EVENT;
            default -> ETC;
        };
    }

    public static SupportProgramTypeTab fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    // ETC 필터에 쓸 '이름 있는 탭 전체'의 원본값 (NOT IN 대상)
    public static List<String> namedRawValues() {
        return Arrays.stream(values())
                .filter(t -> t != ETC)
                .flatMap(t -> t.rawValues.stream())
                .toList();
    }

    // 목록 필터(program_type IN rawValues)와 같은 기준으로 원본값을 탭에 넣는다.
    // of()는 정규화로 폭넓게 잡아 배지 카운트가 필터 결과와 어긋나므로, 배지 카운트에는 이 exact 매칭을 쓴다.
    public static SupportProgramTypeTab fromRawValue(String rawType) {
        if (rawType == null) {
            return ETC;
        }
        return Arrays.stream(values())
                .filter(t -> t != ETC && t.rawValues.contains(rawType))
                .findFirst()
                .orElse(ETC);
    }
}
