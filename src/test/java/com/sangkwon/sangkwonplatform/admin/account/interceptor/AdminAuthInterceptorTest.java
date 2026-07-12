package com.sangkwon.sangkwonplatform.admin.account.interceptor;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.account.session.SessionConst;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    @Mock
    AdminUserRepository adminUserRepository;

    private final HttpServletResponse response = new MockHttpServletResponse();

    private AdminUser admin(Long id, AdminRole role, AdminStatus status) {
        AdminUser a = AdminUser.create("admin", "hash", "관리자", role);
        ReflectionTestUtils.setField(a, "adminId", id);
        if (status != AdminStatus.ACTIVE) {
            a.updateStatus(status);
        }
        return a;
    }

    private MockHttpServletRequest requestWith(AdminSession session) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        // 인증·세션 동기화를 보는 테스트의 기본 경로는 모든 관리자 허용(VIEWER) 경로로 둔다. 인가 테스트는 URI를 따로 지정한다.
        req.setRequestURI("/api/admin/auth/me");
        if (session != null) {
            req.getSession(true).setAttribute(SessionConst.LOGIN_ADMIN, session);
        }
        return req;
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void 세션이_없으면_401() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        assertThatThrownBy(() -> interceptor.preHandle(requestWith(null), response, new Object()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 활성_계정이고_권한이_같으면_통과하고_세션을_그대로_둔다() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L))
                .thenReturn(Optional.of(admin(1L, AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE)));

        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.SUPER_ADMIN, 0));
        assertThat(interceptor.preHandle(req, response, new Object())).isTrue();

        AdminSession after = (AdminSession) req.getSession(false).getAttribute(SessionConst.LOGIN_ADMIN);
        assertThat(after.role()).isEqualTo(AdminRole.SUPER_ADMIN);
    }

    @Test
    void 권한이_바뀌면_세션_권한을_최신으로_교체한다() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L))
                .thenReturn(Optional.of(admin(1L, AdminRole.VIEWER, AdminStatus.ACTIVE)));

        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.SUPER_ADMIN, 0));
        assertThat(interceptor.preHandle(req, response, new Object())).isTrue();

        AdminSession after = (AdminSession) req.getSession(false).getAttribute(SessionConst.LOGIN_ADMIN);
        assertThat(after.role()).isEqualTo(AdminRole.VIEWER);
    }

    @Test
    void 권한이_부족한_경로면_403() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L))
                .thenReturn(Optional.of(admin(1L, AdminRole.VIEWER, AdminStatus.ACTIVE)));
        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.VIEWER, 0));
        req.setRequestURI("/api/admin/payments/o1/cancel"); // SUPER_ADMIN 전용

        assertThatThrownBy(() -> interceptor.preHandle(req, response, new Object()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void VIEWER는_문의_공지_조회는_통과하고_쓰기는_403이다() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L))
                .thenReturn(Optional.of(admin(1L, AdminRole.VIEWER, AdminStatus.ACTIVE)));
        // 조회(GET)는 모든 관리자 허용 -> VIEWER 통과
        MockHttpServletRequest get = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.VIEWER, 0));
        get.setRequestURI("/api/admin/inquiries");
        get.setMethod("GET");
        assertThat(interceptor.preHandle(get, response, new Object())).isTrue();
        // 쓰기(POST)는 OPERATOR 이상 -> VIEWER 403
        MockHttpServletRequest post = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.VIEWER, 0));
        post.setRequestURI("/api/admin/inquiries/5/answer");
        post.setMethod("POST");
        assertThatThrownBy(() -> interceptor.preHandle(post, response, new Object()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void 비_SUPER_ADMIN도_본인_계정_경로는_인터셉터를_통과한다() {
        // admin-users 접두사는 VIEWER 이상이면 인터셉터 통과(본인만/super 세부는 컨트롤러 requireSelf/requireSuperAdmin가 강제)
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L))
                .thenReturn(Optional.of(admin(1L, AdminRole.OPERATOR, AdminStatus.ACTIVE)));
        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.OPERATOR, 0));
        req.setRequestURI("/api/admin/admin-users/1/password");
        req.setMethod("PATCH");
        assertThat(interceptor.preHandle(req, response, new Object())).isTrue();
    }

    @Test
    void 운영자는_운영자_경로에_통과한다() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L))
                .thenReturn(Optional.of(admin(1L, AdminRole.OPERATOR, AdminStatus.ACTIVE)));
        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.OPERATOR, 0));
        req.setRequestURI("/api/admin/inquiries");

        assertThat(interceptor.preHandle(req, response, new Object())).isTrue();
    }

    @Test
    void 매핑되지_않은_admin_경로는_기본_SUPER_ADMIN이라_운영자도_403() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L))
                .thenReturn(Optional.of(admin(1L, AdminRole.OPERATOR, AdminStatus.ACTIVE)));
        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.OPERATOR, 0));
        req.setRequestURI("/api/admin/something-new"); // 매핑 밖 -> fail closed

        assertThatThrownBy(() -> interceptor.preHandle(req, response, new Object()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void 잠긴_계정이면_401() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L))
                .thenReturn(Optional.of(admin(1L, AdminRole.SUPER_ADMIN, AdminStatus.LOCKED)));

        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.SUPER_ADMIN, 0));
        assertThatThrownBy(() -> interceptor.preHandle(req, response, new Object()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 삭제된_계정이면_401() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L)).thenReturn(Optional.empty());

        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.SUPER_ADMIN, 0));
        assertThatThrownBy(() -> interceptor.preHandle(req, response, new Object()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 비밀번호_버전이_다르면_세션을_무효화하고_401() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        AdminUser changed = admin(1L, AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
        changed.updatePassword("newhash"); // pwVersion 0 -> 1
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(changed));

        // 세션은 비번 변경 전(버전 0)에 발급된 것 -> 버전 불일치로 무효화
        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.SUPER_ADMIN, 0));
        assertThatThrownBy(() -> interceptor.preHandle(req, response, new Object()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
