package com.sangkwon.sangkwonplatform.admin.account.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리자 API(로그인 포함)를 허용 IP 대역으로만 제한한다.
 * admin.security.ip-allowlist 가 비어 있으면 제한하지 않는다.
 */
@Component
public class AdminIpInterceptor implements HandlerInterceptor {

    private final IpAllowlist allowlist;

    public AdminIpInterceptor(@Value("${admin.security.ip-allowlist:}") String raw) {
        this.allowlist = IpAllowlist.parse(raw);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (allowlist.isEmpty()) {
            return true;
        }
        if (!allowlist.isAllowed(clientIp(request))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "허용되지 않은 IP에서의 접근입니다.");
        }
        return true;
    }

    // 프록시 뒤라면 X-Forwarded-For의 첫 IP, 아니면 원격 주소
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
