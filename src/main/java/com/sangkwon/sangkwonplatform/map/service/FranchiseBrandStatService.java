package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.response.FranchiseBrandStatResponse;
import com.sangkwon.sangkwonplatform.map.repository.FranchiseBrandStatRepository;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// 업종별 주요 프랜차이즈 조회. 업종·상권 동향 화면(Pro 전용) 카드가 쓴다.
@Service
@Transactional(readOnly = true)
public class FranchiseBrandStatService {

    private final FranchiseBrandStatRepository repository;
    private final MemberRepository memberRepository;

    public FranchiseBrandStatService(FranchiseBrandStatRepository repository, MemberRepository memberRepository) {
        this.repository = repository;
        this.memberRepository = memberRepository;
    }

    // Pro 전용. 비로그인은 SecurityConfig에서 막고, 여기선 Pro 여부로 402를 판정한다.
    public List<FranchiseBrandStatResponse> getTopBrands(Long memberId, String indutyCd) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 정보를 찾을 수 없습니다"));
        if (!member.isPro()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "업종·상권 동향은 Pro 전용이에요. Pro로 업그레이드하면 이용할 수 있어요.");
        }

        return repository.findTop5ByIndutyCdOrderByFrcsCntDescBrandNmAsc(indutyCd)
                .stream().map(FranchiseBrandStatResponse::from).toList();
    }
}
