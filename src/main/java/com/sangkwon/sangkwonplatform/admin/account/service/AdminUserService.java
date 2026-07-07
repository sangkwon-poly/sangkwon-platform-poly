package com.sangkwon.sangkwonplatform.admin.account.service;

import com.sangkwon.sangkwonplatform.admin.account.dto.request.*;
import com.sangkwon.sangkwonplatform.admin.account.dto.response.AdminListResponse;
import com.sangkwon.sangkwonplatform.admin.account.dto.response.OtpSetupResponse;
import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
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

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminLoginAttemptService loginAttemptService;

    // 관리자 계정 생성 (인가는 컨트롤러에서 SUPER_ADMIN으로 제한)
    public void join(AdminJoinRequest request) {
        if (adminUserRepository.existsByLoginId(request.loginId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        AdminUser adminUser = AdminUser.create(
                request.loginId(),
                passwordHash,
                request.adminName(),
                request.role()
        );

        adminUserRepository.save(adminUser);
    }

    // 관리자 목록 조회
    @Transactional(readOnly = true)
    public List<AdminListResponse> getAdminList() {
        return adminUserRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(AdminListResponse::from)
                .toList();
    }

    // 관리자 로그인
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

        boolean matches = passwordEncoder.matches(request.password(), adminUser.getPasswordHash());
        if (!matches) {
            // 실패 카운트/잠금은 별도 트랜잭션에서 확정한다(이 트랜잭션은 아래 예외로 롤백됨)
            loginAttemptService.recordFailure(adminUser.getAdminId());
            throw invalidCredentials();
        }

        // 2단계 인증을 켠 계정은 OTP까지 맞아야 통과
        if (adminUser.isOtpEnabled()) {
            String otp = request.otp();
            if (otp == null || otp.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP 인증코드를 입력하세요.");
            }
            if (!Totp.verify(adminUser.getOtpSecret(), otp)) {
                loginAttemptService.recordFailure(adminUser.getAdminId());
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP 인증코드가 올바르지 않습니다.");
            }
        }

        adminUser.loginSuccess(); // 성공 시 실패 카운트 리셋 (이 트랜잭션에서 커밋)
        return AdminSession.from(adminUser);
    }

    // OTP(2FA) 설정 시작: 비밀키 발급·저장(아직 미활성). 반환된 URL을 인증 앱에 등록한다.
    public OtpSetupResponse setupOtp(Long adminId) {
        AdminUser admin = findAdminUser(adminId);
        String secret = Totp.generateSecret();
        admin.startOtpSetup(secret);
        return new OtpSetupResponse(secret, Totp.otpauthUrl("서울공화국 ADMIN", admin.getLoginId(), secret));
    }

    // 인증 앱 코드로 확인되면 2FA를 활성화한다.
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

    // 현재 설정 중인 비밀키로 인증 앱 등록용 otpauth URL을 만든다 (QR 렌더링에 사용)
    @Transactional(readOnly = true)
    public String otpauthUrlFor(Long adminId) {
        AdminUser admin = findAdminUser(adminId);
        if (admin.getOtpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "먼저 OTP 설정을 시작하세요.");
        }
        return Totp.otpauthUrl("서울공화국 ADMIN", admin.getLoginId(), admin.getOtpSecret());
    }

    // 관리자 이름 수정 (본인)
    public void updateName(Long adminId, AdminNameUpdateRequest request) {
        AdminUser adminUser = findAdminUser(adminId);
        adminUser.updateName(request.adminName());
    }

    // 관리자 비밀번호 수정 (본인)
    public void updatePassword(Long adminId, AdminPasswordUpdateRequest request) {
        AdminUser adminUser = findAdminUser(adminId);

        boolean matches = passwordEncoder.matches(request.currentPassword(), adminUser.getPasswordHash());
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        String passwordHash = passwordEncoder.encode(request.newPassword());
        adminUser.updatePassword(passwordHash);
    }

    // 역할 변경 (SUPER_ADMIN 전용, 컨트롤러에서 제한)
    public void updateRole(Long adminId, AdminRoleUpdateRequest request) {
        findAdminUser(adminId).updateRole(request.role());
    }

    // 상태 변경 (SUPER_ADMIN 전용, 컨트롤러에서 제한)
    public void updateStatus(Long adminId, AdminStatusUpdateRequest request) {
        findAdminUser(adminId).updateStatus(request.status());
    }

    private AdminUser findAdminUser(Long adminId) {
        return adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "관리자를 찾을 수 없습니다."));
    }

    // 아이디 존재 여부가 드러나지 않도록 로그인 실패는 항상 같은 메시지로 응답
    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
