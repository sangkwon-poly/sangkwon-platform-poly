package com.sangkwon.sangkwonplatform.admin.account.session;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code @LoginAdmin AdminSession} 파라미터를 HTTP 세션에서 꺼내 주입한다.
 * 인증 자체는 {@code AdminAuthInterceptor}가 보장하지만, 세션이 비어 있으면 여기서도 401로 막는다.
 */
public class LoginAdminArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginAdmin.class)
                && AdminSession.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpSession session = (request == null) ? null : request.getSession(false);
        Object loginAdmin = (session == null) ? null : session.getAttribute(SessionConst.LOGIN_ADMIN);
        if (loginAdmin == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return loginAdmin;
    }
}
