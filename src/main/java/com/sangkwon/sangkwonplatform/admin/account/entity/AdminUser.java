package com.sangkwon.sangkwonplatform.admin.account.entity;

import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.HashAlgo;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.type.YesNoConverter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ADMIN_USER")
@Getter
public class AdminUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="ADMIN_ID")
    private Long adminId;

    @Column(name="LOGIN_ID",unique = true, length = 50)
    private String loginId;

    @Column(name="PASSWORD_HASH", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name="PW_ALGO", nullable = false, length = 20)
    private HashAlgo pwAlgo = HashAlgo.BCRYPT;

    @Column(name = "name", nullable = false, length = 50)
    private String adminName;

    @Enumerated(EnumType.STRING)
    @Column(name="ROLE", nullable = false, length = 15)
    private AdminRole role = AdminRole.VIEWER;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false, length = 10)
    private AdminStatus status = AdminStatus.ACTIVE;

    @Column(name="failed_login_cnt", nullable = false)
    private int failedLoginCnt = 0;

    // 연속 실패로 자동 잠긴 시각(쿨다운 자동 해제 판정용). 관리자 수동 잠금은 null이라 자동 해제 대상이 아니다.
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    // 비밀번호가 바뀔 때마다 증가한다. 세션에 담아두고 요청마다 비교해, 비번 변경/재설정 시 기존 세션을 무효화한다.
    @Column(name = "PW_VERSION", nullable = false)
    private int pwVersion = 0;

    @Convert(converter = YesNoConverter.class)
    @Column(name = "OTP_ENABLED", nullable = false)
    private boolean otpEnabled = false;

    @Column(name = "OTP_SECRET", length = 64)
    private String otpSecret;

    // 마지막으로 소비한 TOTP 시간 스텝(리플레이 방지용). 아직 사용 이력이 없으면 null.
    @Column(name = "OTP_LAST_STEP")
    private Long otpLastStep;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static AdminUser create(
            String loginId,
            String passwordHash,
            String name,
            AdminRole role
    ) {
        AdminUser adminUser = new AdminUser();
        adminUser.loginId = loginId;
        adminUser.passwordHash = passwordHash;
        adminUser.adminName = name;
        adminUser.role = role == null ? AdminRole.VIEWER : role;
        adminUser.status = AdminStatus.ACTIVE;
        adminUser.pwAlgo = HashAlgo.BCRYPT;
        adminUser.failedLoginCnt = 0;
        return adminUser;
    }

    public void updateName(String name) {
        if (name != null && !name.isBlank()) {
            this.adminName = name;
        }
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.pwAlgo = HashAlgo.BCRYPT;
        this.failedLoginCnt = 0;
        // 비번이 바뀌면 버전을 올려, 이 계정의 다른 기존 세션이 다음 요청에서 즉시 무효화되게 한다
        this.pwVersion++;
    }

    public void updateRole(AdminRole role) {
        if (role != null) {
            this.role = role;
        }
    }

    public void updateStatus(AdminStatus status) {
        if (status != null) {
            this.status = status;
            // 관리자 수동 상태 변경은 자동 잠금 마커를 지운다(수동 잠금은 자동 해제 대상이 아니다)
            this.lockedAt = null;
            // 잠금 해제(활성 전환) 시 실패 카운트를 초기화해 즉시 재잠금되지 않도록 한다
            if (status == AdminStatus.ACTIVE) {
                this.failedLoginCnt = 0;
            }
        }
    }

    public void increaseFailedLoginCnt() {
        this.failedLoginCnt++;
    }

    // 로그인 연속 실패에 의한 자동 잠금. 쿨다운 자동 해제 대상이 되도록 잠금 시각을 남긴다.
    // 이미 잠금 상태면 잠금 시각을 갱신하지 않는다: 잠긴 계정에 실패를 계속 보내 쿨다운을 무한 연장하는
    // 영구 잠금(DoS)을 막는다. 수동 잠금(lockedAt=null)도 재스탬프하지 않아 자동 해제 대상이 되지 않는다.
    public void lockForFailedLogin() {
        if (this.status == AdminStatus.LOCKED) {
            return;
        }
        this.status = AdminStatus.LOCKED;
        this.lockedAt = LocalDateTime.now();
    }

    // 자동 잠금 해제: 활성 복귀 + 실패 카운트/잠금 시각 초기화.
    public void unlock() {
        this.status = AdminStatus.ACTIVE;
        this.failedLoginCnt = 0;
        this.lockedAt = null;
    }

    // 자동 잠금이 쿨다운을 지났는지. 관리자 수동 잠금(lockedAt 없음)은 자동 해제 대상이 아니다.
    public boolean isAutoUnlockable(java.time.Duration cooldown) {
        return this.status == AdminStatus.LOCKED
                && this.lockedAt != null
                && this.lockedAt.plus(cooldown).isBefore(LocalDateTime.now());
    }

    public void loginSuccess() {
        this.failedLoginCnt = 0;
        this.lastLoginAt = LocalDateTime.now();
    }

    // OTP 설정 시작: 비밀키만 저장하고 아직 미활성
    public void startOtpSetup(String secret) {
        this.otpSecret = secret;
        this.otpEnabled = false;
    }

    // 인증 앱 코드 확인 후 2FA 활성화
    public void confirmOtp() {
        this.otpEnabled = true;
    }

    // 이미 사용한 스텝(마지막 스텝 이하)이면 거부한다. 통과하면 그 스텝을 마지막으로 기록해 재사용을 막는다.
    public boolean consumeOtpStep(long step) {
        if (otpLastStep != null && step <= otpLastStep) {
            return false;
        }
        this.otpLastStep = step;
        return true;
    }

    public void disableOtp() {
        this.otpEnabled = false;
        this.otpSecret = null;
        this.otpLastStep = null;
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }



}
