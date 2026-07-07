package com.sangkwon.sangkwonplatform.admin.account.entity;

import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.HashAlgo;
import jakarta.persistence.*;
import lombok.Getter;

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
    }

    public void updateRole(AdminRole role) {
        if (role != null) {
            this.role = role;
        }
    }

    public void updateStatus(AdminStatus status) {
        if (status != null) {
            this.status = status;
        }
    }

    public void increaseFailedLoginCnt() {
        this.failedLoginCnt++;
    }

    public void loginSuccess() {
        this.failedLoginCnt = 0;
        this.lastLoginAt = LocalDateTime.now();
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
