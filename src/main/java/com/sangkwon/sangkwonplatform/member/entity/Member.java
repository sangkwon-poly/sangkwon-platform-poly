package com.sangkwon.sangkwonplatform.member.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "MEMBER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "LOGIN_ID", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 255)
    private String passwordHash;

    // bcrypt는 salt를 해시 문자열 안에 포함하므로 별도 salt는 null (스키마 CHECK와 일치)
    // bcrypt가 아닌 방식을 사용할경우(PW_ALGO!="BCRYPT")
    @Column(name = "PASSWORD_SALT", length = 255)
    private String passwordSalt;

    @Column(name = "PW_ALGO", nullable = false, length = 20)
    private String pwAlgo;

    @Column(name = "EMAIL", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "NICKNAME", nullable = false, length = 50)
    private String nickname;

    //Role 파일 참조 현재 유의미하게 사용할 예정 없음. 확장성
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 10)
    private Role role;

    //MemberStatus 참조
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private MemberStatus status;

    @Column(name = "LAST_LOGIN_AT")
    private LocalDateTime lastLoginAt;

    @Column(name = "WITHDRAWN_AT")
    private LocalDateTime withdrawnAt;

    // 생성은 정적 팩토리로만 → 규칙(기본 role/status, bcrypt 태그)을 한 곳에 모은다
    private Member(String loginId, String passwordHash, String email, String nickname) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.passwordSalt = null;       // bcrypt
        this.pwAlgo = "BCRYPT";
        this.email = email;
        this.nickname = nickname;
        this.role = Role.USER;
        this.status = MemberStatus.ACTIVE;
    }

    // 회원가입용. passwordHash는 서비스에서 bcrypt로 만들어 넘긴다.
    public static Member create(String loginId, String passwordHash, String email, String nickname) {
        return new Member(loginId, passwordHash, email, nickname);
    }


    public void updateProfile(String nickname, String email) {
        if (nickname != null) this.nickname = nickname;
        if (email != null) this.email = email;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.passwordSalt = null;
        this.pwAlgo = "BCRYPT";
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void withdraw() {
        this.status = MemberStatus.WITHDRAWN; // 탈퇴 처리
        this.withdrawnAt = LocalDateTime.now();  // 스키마 CHECK: WITHDRAWN이면 WITHDRAWN_AT 필수 => 탈퇴 시각 필수
    }

    public boolean isActive() {
        return this.status == MemberStatus.ACTIVE;
    }
}