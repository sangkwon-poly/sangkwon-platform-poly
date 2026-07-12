package com.sangkwon.sangkwonplatform.admin.account.service;

import com.sangkwon.sangkwonplatform.admin.account.dto.request.AdminJoinRequest;
import com.sangkwon.sangkwonplatform.admin.account.dto.request.AdminLoginRequest;
import com.sangkwon.sangkwonplatform.admin.account.dto.request.AdminPasswordResetRequest;
import com.sangkwon.sangkwonplatform.admin.account.dto.request.AdminRoleUpdateRequest;
import com.sangkwon.sangkwonplatform.admin.account.dto.request.AdminStatusUpdateRequest;
import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.account.otp.OtpRequiredException;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.account.security.TrustedDeviceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    TrustedDeviceService trustedDeviceService;
    @Mock
    AdminLoginRateLimiter rateLimiter;

    @InjectMocks
    AdminUserService adminUserService;

    private AdminUser activeAdmin() {
        return AdminUser.create("admin", "hash", "관리자", AdminRole.SUPER_ADMIN);
    }

    private AdminUser otpAdmin() {
        AdminUser admin = activeAdmin();
        admin.startOtpSetup("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        admin.confirmOtp();
        return admin;
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void 로그인_성공하면_세션을_반환하고_실패기록을_남기지_않는다() {
        when(adminUserRepository.findByLoginIdForUpdate("admin")).thenReturn(Optional.of(activeAdmin()));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        AdminSession session = adminUserService.login(new AdminLoginRequest("admin", "pw", null, false), null, "1.1.1.1");

        assertThat(session.loginId()).isEqualTo("admin");
        verify(loginAttemptService, never()).recordFailure(any());
    }

    @Test
    void 비밀번호가_틀리면_401과_함께_실패기록을_남긴다() {
        when(adminUserRepository.findByLoginIdForUpdate("admin")).thenReturn(Optional.of(activeAdmin()));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("admin", "wrong", null, false), null, "1.1.1.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(401));
        verify(loginAttemptService).recordFailure(any());
    }

    @Test
    void 존재하지_않는_아이디도_같은_401_메시지로_응답하고_실패기록은_없다() {
        when(adminUserRepository.findByLoginIdForUpdate("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("nope", "pw", null, false), null, "1.1.1.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(401));
        verify(loginAttemptService, never()).recordFailure(any());
    }

    @Test
    void 잠긴_계정은_비밀번호가_맞아도_로그인이_차단된다() {
        AdminUser locked = activeAdmin();
        locked.updateStatus(AdminStatus.LOCKED);
        when(adminUserRepository.findByLoginIdForUpdate("admin")).thenReturn(Optional.of(locked));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true); // 비번은 맞고 상태가 잠금

        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("admin", "pw", null, false), null, "1.1.1.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void 잠긴_계정도_비밀번호가_틀리면_상태를_노출하지_않고_401이다() {
        AdminUser locked = activeAdmin();
        locked.updateStatus(AdminStatus.LOCKED);
        when(adminUserRepository.findByLoginIdForUpdate("admin")).thenReturn(Optional.of(locked));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        // 비번을 먼저 검증해 미인증 계정 열거를 막는다: 잠금 상태(403)가 아니라 일반 401
        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("admin", "wrong", null, false), null, "1.1.1.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(401));
    }

    @Test
    void IP가_레이트리밋에_걸리면_계정_조회_없이_429로_거절한다() {
        when(rateLimiter.isBlocked("9.9.9.9")).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("admin", "pw", null, false), null, "9.9.9.9"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(429));
        verify(adminUserRepository, never()).findByLoginIdForUpdate(any());
    }

    @Test
    void 쿨다운이_지난_자동잠금은_자동_해제되고_로그인이_진행된다() {
        AdminUser locked = activeAdmin();
        locked.lockForFailedLogin();
        // 자동 잠금 시각을 쿨다운(15분) 이전으로 되돌려 자동 해제 대상으로 만든다
        ReflectionTestUtils.setField(locked, "lockedAt", LocalDateTime.now().minusMinutes(20));
        when(adminUserRepository.findByLoginIdForUpdate("admin")).thenReturn(Optional.of(locked));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        AdminSession session = adminUserService.login(new AdminLoginRequest("admin", "pw", null, false), null, "1.1.1.1");

        assertThat(session.loginId()).isEqualTo("admin");
        assertThat(locked.getStatus()).isEqualTo(AdminStatus.ACTIVE);
    }

    @Test
    void 쿨다운이_지나지_않은_자동잠금은_여전히_403이다() {
        AdminUser locked = activeAdmin();
        locked.lockForFailedLogin(); // 방금 자동 잠금(lockedAt=now)
        when(adminUserRepository.findByLoginIdForUpdate("admin")).thenReturn(Optional.of(locked));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true); // 비번은 맞고 잠금 쿨다운 미경과

        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("admin", "pw", null, false), null, "1.1.1.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void OTP를_켠_계정은_비밀번호가_맞아도_코드가_없으면_OTP를_요구한다() {
        when(adminUserRepository.findByLoginIdForUpdate("admin")).thenReturn(Optional.of(otpAdmin()));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("admin", "pw", null, false), null, "1.1.1.1"))
                .isInstanceOf(OtpRequiredException.class);
    }

    @Test
    void 신뢰된_기기면_OTP_코드가_없어도_로그인된다() {
        when(adminUserRepository.findByLoginIdForUpdate("admin")).thenReturn(Optional.of(otpAdmin()));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(trustedDeviceService.verify(any(), any())).thenReturn(true);

        AdminSession session = adminUserService.login(new AdminLoginRequest("admin", "pw", null, false), "trusted-token", "1.1.1.1");

        assertThat(session.loginId()).isEqualTo("admin");
    }

    @Test
    void 중복된_로그인ID로_가입하면_409() {
        when(adminUserRepository.existsByLoginId("dup")).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.join(
                new AdminJoinRequest("dup", "pw", "이름", AdminRole.VIEWER)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void 비밀번호_재설정은_새_비번을_적용하고_잠금을_해제한다() {
        AdminUser locked = activeAdmin();
        locked.increaseFailedLoginCnt();
        locked.updateStatus(AdminStatus.LOCKED);
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(locked));
        when(passwordEncoder.encode("newpw")).thenReturn("newhash");

        adminUserService.resetPassword(1L, new AdminPasswordResetRequest("newpw"));

        assertThat(locked.getPasswordHash()).isEqualTo("newhash");
        assertThat(locked.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(locked.getFailedLoginCnt()).isZero();
    }

    @Test
    void 존재하지_않는_아이디도_더미해시_비교로_응답_시간을_맞춘다() {
        when(adminUserRepository.findByLoginIdForUpdate("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.login(new AdminLoginRequest("nope", "pw", null, false), null, "1.1.1.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(401));
        // 계정이 없어도 비밀번호 비교(더미 해시)를 수행해 타이밍 차이를 없앤다
        verify(passwordEncoder).matches(eq("pw"), any());
    }

    @Test
    void 마지막_활성_최고관리자는_강등할_수_없다() {
        AdminUser sa = activeAdmin();
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(sa));
        when(adminUserRepository.countByRoleAndStatus(AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.updateRole(1L, new AdminRoleUpdateRequest(AdminRole.OPERATOR)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        assertThat(sa.getRole()).isEqualTo(AdminRole.SUPER_ADMIN);
    }

    @Test
    void 활성_최고관리자가_둘_이상이면_강등된다() {
        AdminUser sa = activeAdmin();
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(sa));
        when(adminUserRepository.countByRoleAndStatus(AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE)).thenReturn(2L);

        adminUserService.updateRole(1L, new AdminRoleUpdateRequest(AdminRole.OPERATOR));

        assertThat(sa.getRole()).isEqualTo(AdminRole.OPERATOR);
    }

    @Test
    void 마지막_활성_최고관리자는_비활성할_수_없다() {
        AdminUser sa = activeAdmin();
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(sa));
        when(adminUserRepository.countByRoleAndStatus(AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.updateStatus(1L, new AdminStatusUpdateRequest(AdminStatus.DISABLED)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        assertThat(sa.getStatus()).isEqualTo(AdminStatus.ACTIVE);
    }

    @Test
    void 최고관리자가_아니면_마지막_여부와_무관하게_비활성된다() {
        AdminUser operator = AdminUser.create("op", "hash", "운영자", AdminRole.OPERATOR);
        when(adminUserRepository.findById(2L)).thenReturn(Optional.of(operator));

        adminUserService.updateStatus(2L, new AdminStatusUpdateRequest(AdminStatus.DISABLED));

        assertThat(operator.getStatus()).isEqualTo(AdminStatus.DISABLED);
    }
}
