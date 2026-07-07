package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.dto.request.MemberLoginRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberSignupRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberUpdateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.MemberResponse;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import com.sangkwon.sangkwonplatform.global.security.JwtProvider;
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
    private final JwtProvider jwtProvider;

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

    @Transactional
    public String login(MemberLoginRequest req) { // loginId,password,remember(기억할지 체크)
        Member m = memberRepository.findByLoginId(req.loginId()) // 로그인 id존재하는지 체크
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        // 자격 증명(비밀번호) 먼저 확인 → 그다음 계정 상태 (상태를 비번보다 먼저 노출하지 않기)
        if (!passwordEncoder.matches(req.password(), m.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        switch (m.getStatus()) {           // 상태별로 정확히 구분 (탈퇴/정지/휴면)
            case ACTIVE -> { }             // 정상 → 통과
            case WITHDRAWN -> throw new BusinessException(ErrorCode.WITHDRAWN_MEMBER);
            case BANNED -> throw new BusinessException(ErrorCode.BANNED_MEMBER);
            case DORMANT -> throw new BusinessException(ErrorCode.DORMANT_MEMBER);
        }
        m.recordLogin();
        return jwtProvider.createToken(m.getMemberId(), m.getRole().name());
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
        if (memberId == null) {   // 비인증 요청(토큰 없음)이면 memberId가 null → 500 대신 401
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
