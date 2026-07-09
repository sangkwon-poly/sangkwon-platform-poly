package com.sangkwon.sangkwonplatform.member.exception;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 전역 GlobalExceptionHandler도 MethodArgumentNotValidException을 잡으므로,
// 회원 엔드포인트에서는 이 어드바이스가 먼저 이겨 M400을 유지하도록 순서를 고정한다.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.sangkwon.sangkwonplatform.member")
public class MemberExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        ApiResponse<Void> body = new ApiResponse<>(false, ec.getCode(), ec.getMessage(), null);
        return ResponseEntity.status(ec.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : ErrorCode.INVALID_INPUT.getMessage();
        ApiResponse<Void> body = new ApiResponse<>(false, ErrorCode.INVALID_INPUT.getCode(), msg, null);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus()).body(body);
    }
}
