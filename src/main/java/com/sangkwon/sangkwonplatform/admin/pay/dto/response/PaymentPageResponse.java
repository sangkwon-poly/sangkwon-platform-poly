package com.sangkwon.sangkwonplatform.admin.pay.dto.response;

import java.util.List;

// 결제 목록 페이지 응답. Page 직렬화 대신 프론트가 쓰는 필드만 고정해 노출한다.
public record PaymentPageResponse(
        List<AdminPaymentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    // 회원 검색어에 걸린 회원이 없을 때: 주문 조회 없이 빈 페이지로 답한다.
    public static PaymentPageResponse empty(int page, int size) {
        return new PaymentPageResponse(List.of(), page, size, 0, 0);
    }
}
