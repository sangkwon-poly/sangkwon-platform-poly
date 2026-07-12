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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
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

    // Pro 구독 만료 시각. NULL이거나 지난 시각이면 무료로 본다(등급 컬럼과 별개로 만료가 최종 판정).
    @Column(name = "PLAN_UNTIL")
    private LocalDateTime planUntil;

    // 동시 승인/부여 경합에서 구독 연장 유실을 막는 낙관적 락. 두 결제가 같은 base로 만료를 계산·저장하면
    // 나중 커밋이 먼저 것을 덮어 이중 결제인데 한 번만 반영되던 문제를 방지한다(뒤진 저장은 재조회로 멱등 처리).
    @Version
    @Column(name = "VERSION", nullable = false)
    private long version;

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

    // Pro 구독 활성화/연장. 만료 시각은 결제 서비스가 남은 기간에 이어붙여 계산해 넘긴다.
    public void activatePro(LocalDateTime until) {
        this.role = Role.PREMIUM;
        this.planUntil = until;
    }

    // 구독 회수(관리자 환불·보상 취소용). 등급과 만료를 함께 무료 상태로 되돌린다.
    public void revokePro() {
        this.role = Role.USER;
        this.planUntil = null;
    }

    // 주문에 기록된 실제 부여 기간만 회수해 다른 결제나 관리자 부여분을 남긴다.
    public void reduceSubscription(LocalDateTime grantedFrom, LocalDateTime grantedUntil) {
        if (planUntil == null) {
            return;
        }
        // 이미 끝난 주문의 환불은 현재 별도로 부여된 구독을 줄이지 않는다.
        // 주문 부여기간이 유효한 동안에만 뒤에 이어진 기간에서 해당 주문 길이를 회수한다.
        if (grantedUntil == null || !grantedUntil.isAfter(LocalDateTime.now())) {
            return;
        }
        LocalDateTime reduced = planUntil.minus(Duration.between(grantedFrom, grantedUntil));
        if (reduced.isAfter(LocalDateTime.now())) {
            this.planUntil = reduced;
        } else {
            revokePro();
        }
    }

    // 지금 시점 기준 Pro 이용 자격. 만료가 지나면 등급이 PREMIUM이어도 무효로 본다.
    public boolean isPro() {
        return planUntil != null && planUntil.isAfter(LocalDateTime.now());
    }

    public void withdraw() {
        this.status = MemberStatus.WITHDRAWN; // 탈퇴 처리
        this.withdrawnAt = LocalDateTime.now();  // 스키마 CHECK: WITHDRAWN이면 WITHDRAWN_AT 필수 => 탈퇴 시각 필수
        revokePro(); // 탈퇴하면 구독 자격도 정리한다(구독중 지표·데이터 정합성). 결제 이력 자체는 보존.
        anonymize();
    }

    // 탈퇴 시 개인정보(PII)를 즉시 파기(익명화)한다. 결제·문의 정합성을 위해 회원 레코드 자체는 보존하되,
    // 로그인ID/이메일/닉네임/비밀번호를 회원별 톰스톤 값으로 치환한다. UNIQUE가 풀려 같은 이메일·아이디로 재가입도 가능해진다.
    private void anonymize() {
        String tombstone = "withdrawn_" + this.memberId;
        this.loginId = tombstone;                       // UNIQUE, 원래 아이디 해제
        this.email = tombstone + "@deleted.local";      // UNIQUE, 원래 이메일 해제
        this.nickname = "탈퇴회원";
        this.passwordHash = "WITHDRAWN";                // 유효한 해시가 아니라 어떤 비밀번호와도 일치하지 않음
        this.passwordSalt = null;
    }

    // 관리자 회원 상태 변경(정지/휴면/해제/강제탈퇴). WITHDRAWN이면 탈퇴 시각을 채워 CK_MBR_WITHDRAWN을 만족시킨다.
    public void changeStatus(MemberStatus newStatus) {
        if (newStatus == MemberStatus.WITHDRAWN) {
            withdraw();
            return;
        }
        this.status = newStatus;
    }

    public boolean isActive() {
        return this.status == MemberStatus.ACTIVE;
    }
}