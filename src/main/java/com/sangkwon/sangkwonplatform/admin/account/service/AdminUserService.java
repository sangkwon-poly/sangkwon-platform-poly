package com.sangkwon.sangkwonplatform.admin.account.service;

import com.sangkwon.sangkwonplatform.admin.account.dto.request.*;
import com.sangkwon.sangkwonplatform.admin.account.dto.response.AdminListResponse;
import com.sangkwon.sangkwonplatform.admin.account.dto.response.OtpSetupResponse;
import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.account.otp.OtpRequiredException;
import com.sangkwon.sangkwonplatform.admin.account.otp.Totp;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.account.security.TrustedDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private static final String OTP_ISSUER = "여기콕 ADMIN";
    // 자동 잠금 쿨다운. 경과하면 다음 로그인 시 자동 해제해 계정 잠금 DoS를 완화한다.
    private static final Duration LOCK_COOLDOWN = Duration.ofMinutes(15);

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminLoginAttemptService loginAttemptService;
    private final TrustedDeviceService trustedDeviceService;
    private final AdminLoginRateLimiter rateLimiter;

    // 존재하지 않는 아이디의 응답 시간을 실제 계정과 맞추기 위한 더미 해시(아이디 열거 방지).
    // 실제 인코더로 한 번만 만들어 재사용한다.
    private volatile String dummyHash;

    private String dummyHash() {
        String h = dummyHash;
        if (h == null) {
            h = passwordEncoder.encode("timing-equalizer");
            dummyHash = h;
        }
        return h;
    }

    public void join(AdminJoinRequest request) {
        if (adminUserRepository.existsByLoginId(request.loginId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }
        AdminUser adminUser = AdminUser.create(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.adminName(),
                request.role());
        adminUserRepository.save(adminUser);
    }

    @Transactional(readOnly = true)
    public List<AdminListResponse> getAdminList() {
        return adminUserRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(AdminListResponse::from)
                .toList();
    }

    // 실패 카운트/OTP 소비는 이 트랜잭션에서 확정한다. 자격 실패 예외로는 롤백하지 않아(noRollbackFor)
    // 행 락을 쥔 채 별도 트랜잭션(REQUIRES_NEW)으로 같은 행을 갱신하다 생기던 자기 교착을 피한다.
    @Transactional(noRollbackFor = {ResponseStatusException.class, OtpRequiredException.class})
    public AdminSession login(AdminLoginRequest request, String trustToken, String clientIp) {
        // 자격 검증 전에 접속 IP 슬롯을 원자적으로 선점한다. 동시 요청도 임계를 넘겨 검증되지 않는다.
        if (!rateLimiter.tryAcquire(clientIp)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }

        // 행 잠금으로 조회해 같은 계정의 동시 로그인을 직렬화한다(OTP 리플레이 방지)
        AdminUser adminUser = adminUserRepository.findByLoginIdForUpdate(request.loginId())
                .orElse(null);
        if (adminUser == null) {
            // 계정이 없어도 실제 계정과 비슷한 시간(더미 해시 비교)을 쓰게 해 아이디 열거를 막는다
            passwordEncoder.matches(request.password(), dummyHash());
            throw invalidCredentials();
        }

        // 자격 증명을 먼저 검증한다. 계정 상태(잠금/비활성)를 비번 확인 전에 노출하면 미인증 계정 열거가
        // 되므로(회원 로그인과 방침 통일), 비번이 맞은 뒤에만 상태를 확정한다.
        if (!passwordEncoder.matches(request.password(), adminUser.getPasswordHash())) {
            // 실패 카운트는 이 트랜잭션에서 올리고 커밋한다(noRollbackFor로 자격 실패에도 유지)
            loginAttemptService.recordFailure(adminUser.getAdminId());
            throw invalidCredentials();
        }

        if (adminUser.getStatus() == AdminStatus.LOCKED) {
            // 쿨다운이 지난 자동 잠금은 여기서 자동 해제하고 인증을 계속한다(잠금 DoS 완화). 수동 잠금은 대상 아님.
            if (adminUser.isAutoUnlockable(LOCK_COOLDOWN)) {
                adminUser.unlock();
            } else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "로그인 실패가 반복되어 계정이 잠겼습니다. 잠시 후 다시 시도하거나 관리자에게 문의하세요.");
            }
        }
        if (adminUser.getStatus() == AdminStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비활성화된 관리자 계정입니다.");
        }
        if (adminUser.getStatus() != AdminStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "사용할 수 없는 관리자 계정입니다.");
        }

        // 신뢰된 기기(유효한 신뢰 쿠키)면 OTP 단계를 건너뛴다
        if (adminUser.isOtpEnabled() && !trustedDeviceService.verify(
                trustToken, adminUser.getAdminId(), adminUser.getPwVersion(), adminUser.getOtpSecret())) {
            String otp = request.otp();
            if (otp == null || otp.isBlank()) {
                throw new OtpRequiredException();
            }
            // 코드가 맞는지 확인하고, 같은 코드의 재사용(리플레이)을 막기 위해 사용한 스텝을 소비한다
            long step = Totp.matchedStep(adminUser.getOtpSecret(), otp);
            if (step == Long.MIN_VALUE || !adminUser.consumeOtpStep(step)) {
                loginAttemptService.recordFailure(adminUser.getAdminId());
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP 인증코드가 올바르지 않습니다.");
            }
        }

        adminUser.loginSuccess();
        // 성공해도 IP 실패 카운터를 리셋하지 않는다: 유효한 저권한 자격으로 로그인을 끼워넣어
        // 카운터를 지우고 스프레잉을 이어가는 우회를 막는다. 정상 실패는 슬라이딩 윈도로 자연히 만료된다.
        return AdminSession.from(adminUser);
    }

    @Transactional(readOnly = true)
    public String issueTrustToken(Long adminId) {
        AdminUser admin = findAdminUser(adminId);
        if (!admin.isOtpEnabled()) {
            return null;
        }
        return trustedDeviceService.issue(
                admin.getAdminId(), admin.getPwVersion(), admin.getOtpSecret());
    }

    public OtpSetupResponse setupOtp(Long adminId) {
        AdminUser admin = findAdminUser(adminId);
        String secret = Totp.generateSecret();
        admin.startOtpSetup(secret);
        return new OtpSetupResponse(secret, Totp.otpauthUrl(OTP_ISSUER, admin.getLoginId(), secret));
    }

    public void enableOtp(Long adminId, String otp) {
        AdminUser admin = findAdminUser(adminId);
        if (admin.getOtpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "먼저 OTP 설정을 시작하세요.");
        }
        long step = Totp.matchedStep(admin.getOtpSecret(), otp);
        if (step == Long.MIN_VALUE || !admin.consumeOtpStep(step)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP 코드가 올바르지 않습니다.");
        }
        admin.confirmOtp();
    }

    public void disableOtp(Long adminId) {
        findAdminUser(adminId).disableOtp();
    }

    @Transactional(readOnly = true)
    public boolean isOtpEnabled(Long adminId) {
        return findAdminUser(adminId).isOtpEnabled();
    }

    @Transactional(readOnly = true)
    public String otpauthUrlFor(Long adminId) {
        AdminUser admin = findAdminUser(adminId);
        if (admin.getOtpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "먼저 OTP 설정을 시작하세요.");
        }
        return Totp.otpauthUrl(OTP_ISSUER, admin.getLoginId(), admin.getOtpSecret());
    }

    public void updateName(Long adminId, AdminNameUpdateRequest request) {
        findAdminUser(adminId).updateName(request.adminName());
    }

    // 비밀번호를 바꾸면 pwVersion이 올라 다른 세션은 무효화된다. 본인 세션은 갱신하도록 새 스냅샷을 돌려준다.
    public AdminSession updatePassword(Long adminId, AdminPasswordUpdateRequest request) {
        AdminUser adminUser = findAdminUser(adminId);
        if (!passwordEncoder.matches(request.currentPassword(), adminUser.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        adminUser.updatePassword(passwordEncoder.encode(request.newPassword()));
        return AdminSession.from(adminUser);
    }

    // 최고관리자가 다른 관리자의 비밀번호를 재설정한다(현재 비밀번호 확인 없이). 잠금도 함께 해제한다.
    public void resetPassword(Long adminId, AdminPasswordResetRequest request) {
        AdminUser adminUser = findAdminUser(adminId);
        adminUser.updatePassword(passwordEncoder.encode(request.newPassword()));
        adminUser.updateStatus(AdminStatus.ACTIVE);
    }

    public void updateRole(Long adminId, AdminRoleUpdateRequest request) {
        AdminUser adminUser = findAdminUser(adminId);
        // 마지막 활성 최고관리자를 강등해 관리할 사람이 사라지는 것을 막는다
        if (request.role() != AdminRole.SUPER_ADMIN) {
            requireNotLastActiveSuperAdmin(adminUser);
        }
        adminUser.updateRole(request.role());
    }

    public void updateStatus(Long adminId, AdminStatusUpdateRequest request) {
        AdminUser adminUser = findAdminUser(adminId);
        // 마지막 활성 최고관리자를 잠금·비활성해 로그인 가능한 관리자가 사라지는 것을 막는다
        if (request.status() != AdminStatus.ACTIVE) {
            requireNotLastActiveSuperAdmin(adminUser);
        }
        adminUser.updateStatus(request.status());
    }

    // 활성 최고관리자(SUPER_ADMIN)가 항상 최소 1명 남도록 보장한다. 락아웃 방지.
    private void requireNotLastActiveSuperAdmin(AdminUser target) {
        if (target.getRole() == AdminRole.SUPER_ADMIN && target.getStatus() == AdminStatus.ACTIVE
                && adminUserRepository.findByRoleAndStatusForUpdate(
                        AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE).size() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "마지막 활성 최고관리자는 강등·잠금·비활성할 수 없습니다.");
        }
    }

    private AdminUser findAdminUser(Long adminId) {
        return adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "관리자를 찾을 수 없습니다."));
    }

    // 아이디 존재 여부를 흘리지 않도록 로그인 실패는 항상 같은 메시지
    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
