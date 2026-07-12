package com.sangkwon.sangkwonplatform.admin.account.interceptor;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.account.session.SessionConst;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// /api/admin/** 는 로그인 세션(LOGIN_ADMIN)이 있어야 통과. 없으면 401.
// 매 요청마다 DB에서 계정 상태·권한을 다시 확인해 잠금·권한 변경이 즉시 반영되게 한다.
// 리포지토리는 요청 시점에만 꺼내 쓴다(JPA가 없는 웹 슬라이스 테스트에서도 설정이 로드되도록).
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final Supplier<AdminUserRepository> adminUserRepository;

    // /api/admin/** 경로별 최소 권한. 인가를 컨트롤러마다 복붙하지 않고 여기서 한 번에 강제한다.
    // 가장 구체적인(긴) 접두사가 먼저 매치되도록 정렬해 첫 매치를 쓴다. 매핑 밖 경로는 기본 SUPER_ADMIN
    // (fail closed): 새 관리자 컨트롤러가 인가를 빠뜨려도 저권한에게 열리지 않는다.
    private static final List<Map.Entry<String, AdminRole>> ROLE_RULES = List.of(
            Map.entry("/api/admin/auth", AdminRole.VIEWER),            // 로그인 후 본인 인증·OTP(모든 관리자)
            Map.entry("/api/admin/trdar", AdminRole.VIEWER),           // 상권 데이터 조회(읽기 전용)
            Map.entry("/api/admin/inquiries", AdminRole.OPERATOR),     // 1:1 문의 응대(운영자 이상)
            Map.entry("/api/admin/notices", AdminRole.OPERATOR),       // 공지 관리(운영자 이상)
            Map.entry("/api/admin/admin-users", AdminRole.SUPER_ADMIN),
            Map.entry("/api/admin/members", AdminRole.SUPER_ADMIN),
            Map.entry("/api/admin/ops", AdminRole.SUPER_ADMIN),
            Map.entry("/api/admin/payments", AdminRole.SUPER_ADMIN),
            Map.entry("/api/admin/support-programs", AdminRole.SUPER_ADMIN));

    public AdminAuthInterceptor(Supplier<AdminUserRepository> adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    // 요청 경로에 필요한 최소 권한. 매핑되지 않은 /api/admin/** 는 가장 강한 권한을 요구한다(fail closed).
    static AdminRole requiredRole(String path) {
        for (Map.Entry<String, AdminRole> rule : ROLE_RULES) {
            if (path.equals(rule.getKey()) || path.startsWith(rule.getKey() + "/")) {
                return rule.getValue();
            }
        }
        return AdminRole.SUPER_ADMIN;
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
        // 경로별 최소 권한을 중앙에서 강제한다(현재 DB 권한 기준). 부족하면 403.
        if (!current.getRole().atLeast(requiredRole(request.getRequestURI()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 작업에 필요한 권한이 없습니다.");
        }
        return true;
    }
}
