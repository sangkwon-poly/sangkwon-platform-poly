package com.sangkwon.sangkwonplatform.admin.account.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 감사 로그 등에서 쓰는 접속 IP 판정. 신뢰 프록시 뒤일 때만 X-Forwarded-For의 가장 오른쪽(프록시가 찍은) 값을 쓴다.
@Component
public class ClientIpResolver {

    private final boolean trustForwardedFor;

    public ClientIpResolver(@Value("${admin.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // 클라이언트가 조작 가능한 왼쪽이 아니라, 신뢰 프록시가 찍은 가장 오른쪽 값을 접속 IP로 본다
                String[] hops = forwarded.split(",");
                return hops[hops.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
