package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.dto.request.MemberLoginRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberSignupRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberUpdateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.MemberResponse;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberLoginRateLimiter rateLimiter;

    // 가입 화면 실시간 중복 확인용
    public boolean isLoginIdAvailable(String loginId) {
        return loginId != null && !loginId.isBlank() && !memberRepository.existsByLoginId(loginId);
    }

    public boolean isEmailAvailable(String email) {
        return email != null && !email.isBlank() && !memberRepository.existsByEmail(email);
    }

    @Transactional
    public MemberResponse signup(MemberSignupRequest req) { //loginId,email,nickname,password dto
        if (memberRepository.existsByLoginId(req.loginId())) { // loginId 중복 체크
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (memberRepository.existsByEmail(req.email())) { // email 중복 체크
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        String hash = passwordEncoder.encode(req.password()); // 비밀번호 해싱
        Member m = Member.create(req.loginId(), hash, req.email(), req.nickname());
        memberRepository.save(m);
        return MemberResponse.from(m);
    }

    // 자격 실패 예외로도 롤백하지 않아(noRollbackFor) 요청 전에 선점한 레이트리밋 슬롯을 유지한다.
    @Transactional(noRollbackFor = BusinessException.class)
    public MemberResponse login(MemberLoginRequest req, String clientIp) { // loginId,password,remember(기억할지 체크)
        // 요청 IP별로 실패를 센다. 아이디 단위로 세면 남의 아이디로 일부러 잠그는 표적 잠금이 가능하다
        String key = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp;
        if (!rateLimiter.tryAcquire(key)) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }

        Member m = memberRepository.findByLoginId(req.loginId()).orElse(null);
        // 아이디 없음과 비밀번호 틀림을 같은 코드로 응답해 계정 열거를 막는다
        if (m == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 자격 증명(비밀번호) 먼저 확인 → 그다음 계정 상태 (상태를 비번보다 먼저 노출하지 않기)
        if (!passwordEncoder.matches(req.password(), m.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        // 성공해도 IP 실패 카운터를 비우지 않는다: 공격자가 자기 계정 로그인을 성공시켜 카운터를 지우고
        // 무제한 스프레잉하는 우회를 막는다(관리자 로그인과 동일). 실패는 슬라이딩 윈도로 자연 만료된다.

        requireActive(m);
        m.recordLogin(); // 마지막 로그인 시간 기록.
        return MemberResponse.from(m);
    }

    public MemberResponse getMe(Long memberId) {
        return MemberResponse.from(find(memberId));
    }

    @Transactional
    public MemberResponse updateMe(Long memberId, MemberUpdateRequest req) {
        Member m = find(memberId);
        // 이메일을 '다른 값'으로 바꿀 때만 중복 검사 (본인 현재 이메일은 제외) → UNIQUE 위반 500 대신 409
        if (req.email() != null && !req.email().equals(m.getEmail())
                && memberRepository.existsByEmail(req.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        m.updateProfile(req.nickname(), req.email());
        return MemberResponse.from(m);
    }

    @Transactional
    public void withdraw(Long memberId) {
        find(memberId).withdraw();
    }
    //탈퇴 상태로 업데이트

    private Member find(Long memberId) {
        if (memberId == null) {   // 비인증 요청이면 memberId가 null → 500 대신 401
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        Member m = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        // 로그인 이후 상태가 바뀌어도(정지·탈퇴·휴면) 남은 세션으로 접근하지 못하게 요청마다 다시 확인한다
        requireActive(m);
        return m;
    }

    // 상태별로 정확히 구분 (탈퇴/정지/휴면). 로그인과 인증된 요청에서 공용으로 쓴다
    private static void requireActive(Member m) {
        switch (m.getStatus()) {
            case ACTIVE -> { }
            case WITHDRAWN -> throw new BusinessException(ErrorCode.WITHDRAWN_MEMBER);
            case BANNED -> throw new BusinessException(ErrorCode.BANNED_MEMBER);
            case DORMANT -> throw new BusinessException(ErrorCode.DORMANT_MEMBER);
        }
    }
}
