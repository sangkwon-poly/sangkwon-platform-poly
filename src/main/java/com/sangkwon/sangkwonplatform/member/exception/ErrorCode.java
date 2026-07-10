package com.sangkwon.sangkwonplatform.member.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "회원을 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "M002", "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "M003", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "M004", "아이디 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "M005", "인증이 필요합니다."),
    DUPLICATE_FAVORITE(HttpStatus.CONFLICT, "M006", "이미 찜한 상권입니다."),
    WITHDRAWN_MEMBER(HttpStatus.FORBIDDEN, "M007", "탈퇴한 회원입니다."),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "M008", "찜한 상권을 찾을 수 없습니다."),
    BANNED_MEMBER(HttpStatus.FORBIDDEN, "M009", "이용이 정지된 계정입니다."),
    DORMANT_MEMBER(HttpStatus.FORBIDDEN, "M010", "휴면 계정입니다. 재활성화가 필요합니다."),
    LOGIN_ID_NOT_FOUND(HttpStatus.UNAUTHORIZED, "M011", "존재하지 않는 아이디입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "M012", "비밀번호가 틀렸습니다."),
    PAYMENT_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "M013", "결제 주문을 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "M014", "결제 금액이 주문 금액과 일치하지 않습니다."),
    PAYMENT_CONFIRM_FAILED(HttpStatus.BAD_GATEWAY, "M015", "결제 승인에 실패했습니다."),
    PAYMENT_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "M016", "결제 기능이 아직 설정되지 않았습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "M400", "입력값이 올바르지 않습니다."),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "M429", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
