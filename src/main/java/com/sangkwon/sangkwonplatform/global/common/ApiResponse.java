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
}
