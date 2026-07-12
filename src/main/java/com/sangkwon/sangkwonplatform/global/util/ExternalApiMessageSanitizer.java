package com.sangkwon.sangkwonplatform.global.util;

import java.util.regex.Pattern;

// 외부 API 호출 실패 메시지에 쿼리 키가 그대로 남지 않게 마스킹한다.
// 배치 이력, 로그, 웹훅으로 URI가 전달될 때 키 유출을 막는다.
public final class ExternalApiMessageSanitizer {

    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)(serviceKey|apiKey|accessKey|secretKey|client_secret)=[^&\\s\"']+");

    private ExternalApiMessageSanitizer() {
    }

    public static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        return SENSITIVE_QUERY.matcher(message).replaceAll("$1=***");
    }

    public static String sanitize(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return sanitize(message);
    }
}
