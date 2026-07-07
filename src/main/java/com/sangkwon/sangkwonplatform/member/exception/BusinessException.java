package com.sangkwon.sangkwonplatform.member.exception;

// 도메인 규칙 위반 예외. throw new BusinessException(ErrorCode.XXX) 로 사용(try-catch 남발 금지).
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
