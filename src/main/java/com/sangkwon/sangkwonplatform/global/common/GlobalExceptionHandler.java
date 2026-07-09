package com.sangkwon.sangkwonplatform.global.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

// 예외를 성공 응답과 같은 ApiResponse 봉투로 내려 에러 계약을 일관되게 유지한다.
// 표준 MVC 예외(404/405 등)는 Spring 기본 처리에 맡기고, 앱이 던지는 예외만 감싼다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 서비스 계층이 던지는 도메인 예외 (예: 상권 없음 404, AI 실패 502)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleStatus(ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        String code = (status instanceof HttpStatus hs) ? hs.name() : String.valueOf(status.value());
        return ResponseEntity.status(status).body(ApiResponse.error(code, e.getReason()));
    }

    // 쿼리 파라미터 타입 변환 실패 (예: ?baseYear=abc) -> 400을 봉투로
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", "요청 파라미터 형식이 올바르지 않습니다: " + e.getName()));
    }

    // 본문 JSON 파싱 실패 -> 400을 봉투로 (기본 /error 스키마로 새지 않게)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", "요청 본문을 읽을 수 없습니다."));
    }

    // UNIQUE 등 제약 위반 -> 409를 봉투로. check-then-act 경쟁으로 새는 500을 여기서 잡는다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONFLICT", "이미 존재하거나 제약에 맞지 않는 값입니다."));
    }
}
