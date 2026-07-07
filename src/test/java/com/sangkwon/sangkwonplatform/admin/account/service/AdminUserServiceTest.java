package com.sangkwon.sangkwonplatform.admin.account.service;

import com.sangkwon.sangkwonplatform.admin.account.dto.request.AdminJoinRequest;
import com.sangkwon.sangkwonplatform.admin.account.dto.request.AdminLoginRequest;
import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.account.otp.OtpRequiredException;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    AdminUserRepository adminUserRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    AdminLoginAttemptService loginAttemptService;

    @InjectMocks
    AdminUserService adminUserService;

    private AdminUser activeAdmin() {
        return AdminUser.create("admin", "hash", "관리자", AdminRole.SUPER_ADMIN);
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void 로그인_성공하면_세션을_반환하고_실패기록을_남기지_않는다() {
        when(adminUserRepository.findByLoginId("admin")).thenReturn(Optional.of(activeAdmin()));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        AdminSession session = adminUserService.login(new AdminLoginRequest("admin", "pw", null));

        assertThat(session.loginId()).isEqualTo("admin");
        verify(loginAttemptService, never()).recordFailure(any());
    }

    @Test
    void 비밀번호가_틀리면_401과_함께_실패기록을_남긴다() {
        when(adminUserRepository.findByLoginId("admin")).thenReturn(Optional.of(activeAdmin()));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("admin", "wrong", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(401));
        verify(loginAttemptService).recordFailure(any());
    }

    @Test
    void 존재하지_않는_아이디도_같은_401_메시지로_응답하고_실패기록은_없다() {
        when(adminUserRepository.findByLoginId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("nope", "pw", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(401));
        verify(loginAttemptService, never()).recordFailure(any());
    }

    @Test
    void 잠긴_계정은_로그인이_차단된다() {
        AdminUser locked = activeAdmin();
        locked.updateStatus(AdminStatus.LOCKED);
        when(adminUserRepository.findByLoginId("admin")).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("admin", "pw", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void OTP를_켠_계정은_비밀번호가_맞아도_코드가_없으면_OTP를_요구한다() {
        AdminUser otpAdmin = activeAdmin();
        otpAdmin.startOtpSetup("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        otpAdmin.confirmOtp();
        when(adminUserRepository.findByLoginId("admin")).thenReturn(Optional.of(otpAdmin));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("admin", "pw", null)))
                .isInstanceOf(OtpRequiredException.class);
    }

    @Test
    void 중복된_로그인ID로_가입하면_409() {
        when(adminUserRepository.existsByLoginId("dup")).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.join(
                new AdminJoinRequest("dup", "pw", "이름", AdminRole.VIEWER)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }
}
