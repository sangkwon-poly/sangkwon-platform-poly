package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.dto.request.MemberLoginRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberSignupRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberUpdateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.MemberResponse;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import com.sangkwon.sangkwonplatform.member.security.JwtProvider;
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
    public MemberResponse signup(MemberSignupRequest req) {
        if (memberRepository.existsByLoginId(req.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (memberRepository.existsByEmail(req.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        String hash = passwordEncoder.encode(req.password());
        Member m = Member.create(req.loginId(), hash, req.email(), req.nickname());
        memberRepository.save(m);
        return MemberResponse.from(m);
    }

    @Transactional
    public String login(MemberLoginRequest req) {
        Member m = memberRepository.findByLoginId(req.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!m.isActive()) {
            throw new BusinessException(ErrorCode.WITHDRAWN_MEMBER);
        }
        if (!passwordEncoder.matches(req.password(), m.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
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
        m.updateProfile(req.nickname(), req.email());
        return MemberResponse.from(m);
    }

    @Transactional
    public void withdraw(Long memberId) {
        find(memberId).withdraw();
    }

    private Member find(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
