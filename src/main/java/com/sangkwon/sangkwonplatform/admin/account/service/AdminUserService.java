package com.sangkwon.sangkwonplatform.admin.account.service;

import com.sangkwon.sangkwonplatform.admin.account.dto.request.*;
import com.sangkwon.sangkwonplatform.admin.account.dto.response.AdminListResponse;
import com.sangkwon.sangkwonplatform.admin.account.dto.response.OtpSetupResponse;
import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.account.otp.OtpRequiredException;
import com.sangkwon.sangkwonplatform.admin.account.otp.Totp;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private static final String OTP_ISSUER = "서울공화국 ADMIN";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminLoginAttemptService loginAttemptService;

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

    public AdminSession login(AdminLoginRequest request) {
        AdminUser adminUser = adminUserRepository.findByLoginId(request.loginId())
                .orElseThrow(this::invalidCredentials);

        if (adminUser.getStatus() == AdminStatus.LOCKED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "로그인 실패가 반복되어 계정이 잠겼습니다. 관리자에게 문의하세요.");
        }
        if (adminUser.getStatus() != AdminStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "사용할 수 없는 관리자 계정입니다.");
        }

        if (!passwordEncoder.matches(request.password(), adminUser.getPasswordHash())) {
            // 이 트랜잭션은 예외로 롤백되므로 실패 카운트는 별도 트랜잭션에서 확정한다
            loginAttemptService.recordFailure(adminUser.getAdminId());
            throw invalidCredentials();
        }

        if (adminUser.isOtpEnabled()) {
            String otp = request.otp();
            if (otp == null || otp.isBlank()) {
                throw new OtpRequiredException();
            }
            if (!Totp.verify(adminUser.getOtpSecret(), otp)) {
                loginAttemptService.recordFailure(adminUser.getAdminId());
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP 인증코드가 올바르지 않습니다.");
            }
        }

        adminUser.loginSuccess();
        return AdminSession.from(adminUser);
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
        if (!Totp.verify(admin.getOtpSecret(), otp)) {
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

    public void updatePassword(Long adminId, AdminPasswordUpdateRequest request) {
        AdminUser adminUser = findAdminUser(adminId);
        if (!passwordEncoder.matches(request.currentPassword(), adminUser.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        adminUser.updatePassword(passwordEncoder.encode(request.newPassword()));
    }

    public void updateRole(Long adminId, AdminRoleUpdateRequest request) {
        findAdminUser(adminId).updateRole(request.role());
    }

    public void updateStatus(Long adminId, AdminStatusUpdateRequest request) {
        findAdminUser(adminId).updateStatus(request.status());
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
