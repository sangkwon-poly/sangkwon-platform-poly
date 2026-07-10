package com.sangkwon.sangkwonplatform.admin.trdar.dto;

import java.util.List;

// 상권 데이터 완결성/품질 점검: 분기별로 팩트 6종의 커버리지 + 품질 플래그
public record TrdarHealthResponse(
        String quarter,
        long totalDistricts,
        List<Fact> facts,
        Flags flags
) {
    // 팩트 테이블 하나의 적재 현황
    public record Fact(String code, String label, long districts, long rows, double coverage) {
    }

    // 품질 플래그: 고아 상권(TRDAR에 없는 코드), 값이 0인 상권 수
    public record Flags(long orphanDistricts, long zeroSales, long zeroStore, long zeroFlpop) {
    }
}
