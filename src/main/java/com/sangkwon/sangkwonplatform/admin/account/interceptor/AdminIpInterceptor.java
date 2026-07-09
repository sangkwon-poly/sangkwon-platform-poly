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
    private final boolean trustForwardedFor;

    public AdminIpInterceptor(
            @Value("${admin.security.ip-allowlist:}") String allowlistRaw,
            @Value("${admin.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.allowlist = IpAllowlist.parse(allowlistRaw);
        this.trustForwardedFor = trustForwardedFor;
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

    private String clientIp(HttpServletRequest request) {
        // X-Forwarded-For는 클라이언트가 임의로 넣을 수 있어, 신뢰 프록시 뒤일 때만 사용한다
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // 신뢰 프록시가 실제 접속 IP를 오른쪽 끝에 덧붙이므로, 클라이언트가 조작할 수 있는
                // 왼쪽이 아니라 가장 오른쪽(프록시가 찍은) 값을 접속 IP로 본다
                String[] hops = forwarded.split(",");
                return hops[hops.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
