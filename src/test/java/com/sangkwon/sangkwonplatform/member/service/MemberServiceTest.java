package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.dto.request.MemberLoginRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberSignupRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberUpdateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.MemberResponse;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.MemberStatus;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

// MemberService 단위 테스트: mock으로 로직만 검증 (DB/Spring 미기동).
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock MemberLoginRateLimiter rateLimiter;

    @InjectMocks MemberService memberService;

    // 기본 상태(ACTIVE) 회원. 비밀번호 해시는 "hashed-pw"로 고정.
    private Member activeMember() {
        return Member.create("minhyuk", "hashed-pw", "min@test.com", "민혁");
    }

    private ErrorCode errorCodeOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    // ---------- 회원가입 ----------

    @Test
    @DisplayName("회원가입: 정상 → 저장하고 응답 반환")
    void signup_success() {
        var req = new MemberSignupRequest("minhyuk", "min@test.com", "민혁", "password1");
        when(memberRepository.existsByLoginId("minhyuk")).thenReturn(false);
        when(memberRepository.existsByEmail("min@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashed-pw");

        MemberResponse res = memberService.signup(req);

        assertThat(res.loginId()).isEqualTo("minhyuk");
        assertThat(res.email()).isEqualTo("min@test.com");
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("회원가입: 로그인ID 중복 → M002")
    void signup_duplicateLoginId() {
        var req = new MemberSignupRequest("minhyuk", "min@test.com", "민혁", "password1");
        when(memberRepository.existsByLoginId("minhyuk")).thenReturn(true);

        assertThatThrownBy(() -> memberService.signup(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.DUPLICATE_LOGIN_ID));
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 이메일 중복 → M003")
    void signup_duplicateEmail() {
        var req = new MemberSignupRequest("minhyuk", "min@test.com", "민혁", "password1");
        when(memberRepository.existsByLoginId("minhyuk")).thenReturn(false);
        when(memberRepository.existsByEmail("min@test.com")).thenReturn(true);

        assertThatThrownBy(() -> memberService.signup(req))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.DUPLICATE_EMAIL));
    }

    // ---------- 로그인 ----------

    @Test
    @DisplayName("로그인: 정상(ACTIVE) → 회원정보 반환 + 로그인시각 기록")
    void login_success() {
        Member m = activeMember();
        var req = new MemberLoginRequest("minhyuk", "password1", false);
        when(memberRepository.findByLoginId("minhyuk")).thenReturn(Optional.of(m));
        when(passwordEncoder.matches("password1", "hashed-pw")).thenReturn(true);

        MemberResponse res = memberService.login(req, "1.1.1.1");

        assertThat(res.loginId()).isEqualTo("minhyuk");
        assertThat(m.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("로그인: 없는 아이디 → M004 (아이디/비번 구분 없이)")
    void login_noSuchId() {
        var req = new MemberLoginRequest("nope", "password1", false);
        when(memberRepository.findByLoginId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.login(req, "1.1.1.1"))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("로그인: 비밀번호 불일치 → M004 (상태보다 먼저 검증)")
    void login_wrongPassword() {
        Member m = activeMember();
        var req = new MemberLoginRequest("minhyuk", "wrong", false);
        when(memberRepository.findByLoginId("minhyuk")).thenReturn(Optional.of(m));
        when(passwordEncoder.matches("wrong", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(() -> memberService.login(req, "1.1.1.1"))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("로그인: 탈퇴 회원 → M007")
    void login_withdrawn() {
        Member m = activeMember();
        // 상태 가드만 격리 검증한다(login_banned와 동일 방식). withdraw()는 PII 익명화까지 하므로 여기선 상태만 세팅.
        ReflectionTestUtils.setField(m, "status", MemberStatus.WITHDRAWN);
        var req = new MemberLoginRequest("minhyuk", "password1", false);
        when(memberRepository.findByLoginId("minhyuk")).thenReturn(Optional.of(m));
        when(passwordEncoder.matches("password1", "hashed-pw")).thenReturn(true);

        assertThatThrownBy(() -> memberService.login(req, "1.1.1.1"))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.WITHDRAWN_MEMBER));
    }

    @Test
    @DisplayName("로그인: 정지 회원 → M009")
    void login_banned() {
        Member m = activeMember();
        ReflectionTestUtils.setField(m, "status", MemberStatus.BANNED);
        var req = new MemberLoginRequest("minhyuk", "password1", false);
        when(memberRepository.findByLoginId("minhyuk")).thenReturn(Optional.of(m));
        when(passwordEncoder.matches("password1", "hashed-pw")).thenReturn(true);

        assertThatThrownBy(() -> memberService.login(req, "1.1.1.1"))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.BANNED_MEMBER));
    }

    @Test
    @DisplayName("로그인: 휴면 회원 → M010")
    void login_dormant() {
        Member m = activeMember();
        ReflectionTestUtils.setField(m, "status", MemberStatus.DORMANT);
        var req = new MemberLoginRequest("minhyuk", "password1", false);
        when(memberRepository.findByLoginId("minhyuk")).thenReturn(Optional.of(m));
        when(passwordEncoder.matches("password1", "hashed-pw")).thenReturn(true);

        assertThatThrownBy(() -> memberService.login(req, "1.1.1.1"))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.DORMANT_MEMBER));
    }

    // ---------- 내정보 / 인증 가드 ----------

    @Test
    @DisplayName("내정보: 비인증(memberId=null) → M005")
    void getMe_unauthenticated() {
        assertThatThrownBy(() -> memberService.getMe(null))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    @DisplayName("내정보: 없는 회원 → M001")
    void getMe_notFound() {
        when(memberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMe(999L))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    // ---------- 정보 수정 ----------

    @Test
    @DisplayName("정보수정: 같은 이메일이면 중복검사 스킵하고 수정")
    void updateMe_sameEmail() {
        Member m = activeMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));
        var req = new MemberUpdateRequest("새닉네임", "min@test.com"); // 기존과 동일 이메일

        MemberResponse res = memberService.updateMe(1L, req);

        assertThat(res.nickname()).isEqualTo("새닉네임");
        verify(memberRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("정보수정: 다른 이메일로 변경 + 중복 → M003")
    void updateMe_duplicateEmail() {
        Member m = activeMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));
        when(memberRepository.existsByEmail("taken@test.com")).thenReturn(true);
        var req = new MemberUpdateRequest("민혁", "taken@test.com");

        assertThatThrownBy(() -> memberService.updateMe(1L, req))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.DUPLICATE_EMAIL));
    }

    @Test
    @DisplayName("정보수정: 다른 이메일 + 미중복 → 수정 성공")
    void updateMe_newEmailOk() {
        Member m = activeMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));
        when(memberRepository.existsByEmail("new@test.com")).thenReturn(false);
        var req = new MemberUpdateRequest("민혁", "new@test.com");

        MemberResponse res = memberService.updateMe(1L, req);

        assertThat(res.email()).isEqualTo("new@test.com");
    }

    // ---------- 탈퇴 ----------

    @Test
    @DisplayName("탈퇴: 상태를 WITHDRAWN으로 변경 + 탈퇴시각 기록 + PII 익명화")
    void withdraw_success() {
        Member m = activeMember();
        org.springframework.test.util.ReflectionTestUtils.setField(m, "memberId", 1L);
        String beforeLoginId = m.getLoginId();
        String beforeEmail = m.getEmail();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));

        memberService.withdraw(1L);

        assertThat(m.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(m.getWithdrawnAt()).isNotNull();
        // PII가 톰스톤 값으로 파기되어 원래 아이디·이메일이 재가입용으로 해제된다
        assertThat(m.getLoginId()).isNotEqualTo(beforeLoginId).isEqualTo("withdrawn_1");
        assertThat(m.getEmail()).isNotEqualTo(beforeEmail).isEqualTo("withdrawn_1@deleted.local");
        assertThat(m.getNickname()).isEqualTo("탈퇴회원");
        assertThat(m.getPasswordHash()).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("탈퇴: 비인증(null) → M005")
    void withdraw_unauthenticated() {
        assertThatThrownBy(() -> memberService.withdraw(null))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }
}
