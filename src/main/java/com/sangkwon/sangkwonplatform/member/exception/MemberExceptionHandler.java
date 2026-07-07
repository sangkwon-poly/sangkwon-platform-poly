package com.sangkwon.sangkwonplatform.member.exception;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// member 도메인 전용 예외 처리 (basePackages로 스코프 한정 → 다른 도메인 미영향). 후속 global 통합.
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
