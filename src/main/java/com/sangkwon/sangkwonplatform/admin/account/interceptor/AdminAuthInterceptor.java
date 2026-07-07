package com.sangkwon.sangkwonplatform.admin.account.interceptor;

import com.sangkwon.sangkwonplatform.admin.account.session.SessionConst;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

// /api/admin/** 는 로그인 세션(LOGIN_ADMIN)이 있어야 통과. 없으면 401.
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Object loginAdmin = (session == null) ? null : session.getAttribute(SessionConst.LOGIN_ADMIN);
        if (loginAdmin == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return true;
    }
}
