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
        if (session != null) {
            req.getSession(true).setAttribute(SessionConst.LOGIN_ADMIN, session);
        }
        return req;
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

        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.SUPER_ADMIN));
        assertThat(interceptor.preHandle(req, response, new Object())).isTrue();

        AdminSession after = (AdminSession) req.getSession(false).getAttribute(SessionConst.LOGIN_ADMIN);
        assertThat(after.role()).isEqualTo(AdminRole.SUPER_ADMIN);
    }

    @Test
    void 권한이_바뀌면_세션_권한을_최신으로_교체한다() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L))
                .thenReturn(Optional.of(admin(1L, AdminRole.VIEWER, AdminStatus.ACTIVE)));

        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.SUPER_ADMIN));
        assertThat(interceptor.preHandle(req, response, new Object())).isTrue();

        AdminSession after = (AdminSession) req.getSession(false).getAttribute(SessionConst.LOGIN_ADMIN);
        assertThat(after.role()).isEqualTo(AdminRole.VIEWER);
    }

    @Test
    void 잠긴_계정이면_401() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L))
                .thenReturn(Optional.of(admin(1L, AdminRole.SUPER_ADMIN, AdminStatus.LOCKED)));

        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.SUPER_ADMIN));
        assertThatThrownBy(() -> interceptor.preHandle(req, response, new Object()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 삭제된_계정이면_401() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(() -> adminUserRepository);
        when(adminUserRepository.findById(1L)).thenReturn(Optional.empty());

        MockHttpServletRequest req = requestWith(new AdminSession(1L, "admin", "관리자", AdminRole.SUPER_ADMIN));
        assertThatThrownBy(() -> interceptor.preHandle(req, response, new Object()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
