package com.sangkwon.sangkwonplatform.global.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

// 예외를 성공 응답과 같은 ApiResponse 봉투로 내려 에러 계약을 일관되게 유지한다.
// 표준 MVC 예외(404/405 등)는 Spring 기본 처리에 맡기고, 앱이 던지는 예외만 감싼다.
@Slf4j
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

    // 필수 요청 파라미터 누락 (예: ?loginId 빠짐) -> 400을 봉투로
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", "필수 요청 파라미터가 없습니다: " + e.getParameterName()));
    }

    // 본문 JSON 파싱 실패 -> 400을 봉투로 (기본 /error 스키마로 새지 않게)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", "요청 본문을 읽을 수 없습니다."));
    }

    // 본문 검증(@Valid) 실패 -> 400을 봉투로. 회원 도메인은 MemberExceptionHandler가 먼저 잡아 M400을 유지한다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError != null && fieldError.getDefaultMessage() != null)
                ? fieldError.getDefaultMessage()
                : "입력값이 올바르지 않습니다.";
        return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", message));
    }

    // UNIQUE 등 제약 위반 -> 409를 봉투로. check-then-act 경쟁으로 새는 500을 여기서 잡는다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONFLICT", "이미 존재하거나 제약에 맞지 않는 값입니다."));
    }

    // 위에서 잡지 못한 런타임 예외(진짜 서버 오류)는 스택트레이스를 로그로 남기고 일반 500을 봉투로 내린다.
    // 내부 메시지를 응답에 노출하지 않아 정보 유출을 막고, 추적은 로그로 한다.
    // 단, Spring MVC 표준 예외(415/406 등)는 다시 던져 프레임워크의 올바른 상태코드 처리에 맡긴다.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(RuntimeException e) {
        if (e.getClass().getName().startsWith("org.springframework.web.")) {
            throw e;
        }
        log.error("처리되지 않은 서버 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }
}
