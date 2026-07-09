package com.sangkwon.sangkwonplatform.admin.account.interceptor;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.account.session.SessionConst;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.function.Supplier;

// /api/admin/** 는 로그인 세션(LOGIN_ADMIN)이 있어야 통과. 없으면 401.
// 매 요청마다 DB에서 계정 상태·권한을 다시 확인해 잠금·권한 변경이 즉시 반영되게 한다.
// 리포지토리는 요청 시점에만 꺼내 쓴다(JPA가 없는 웹 슬라이스 테스트에서도 설정이 로드되도록).
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final Supplier<AdminUserRepository> adminUserRepository;

    public AdminAuthInterceptor(Supplier<AdminUserRepository> adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Object attr = (session == null) ? null : session.getAttribute(SessionConst.LOGIN_ADMIN);
        if (!(attr instanceof AdminSession loginAdmin)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        // 로그인 시점 스냅샷이 아니라 현재 DB 상태로 인가한다: 잠기거나 삭제된 계정은 기존 세션도 즉시 차단
        AdminUser current = adminUserRepository.get().findById(loginAdmin.adminId()).orElse(null);
        if (current == null || current.getStatus() != AdminStatus.ACTIVE) {
            session.invalidate();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        // 비밀번호가 바뀐 뒤(버전 불일치) 발급된 세션이면 무효화: 비번 변경/재설정 시 다른 세션 강제 로그아웃
        if (current.getPwVersion() != loginAdmin.pwVersion()) {
            session.invalidate();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        // 권한이 바뀌었으면 세션의 권한 스냅샷을 최신으로 교체(강등·승급 즉시 반영)
        if (current.getRole() != loginAdmin.role()) {
            session.setAttribute(SessionConst.LOGIN_ADMIN, AdminSession.from(current));
        }
        return true;
    }
}
