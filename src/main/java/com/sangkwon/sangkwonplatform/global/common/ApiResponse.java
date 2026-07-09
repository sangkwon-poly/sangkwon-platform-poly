package com.sangkwon.sangkwonplatform.global.common;

// API 공통 응답 포맷
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", null, data);
    }

    // 실패 응답도 같은 봉투로 내려 code/message가 의미를 갖도록 한다 (GlobalExceptionHandler에서 사용)
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
