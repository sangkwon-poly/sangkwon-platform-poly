package com.sangkwon.sangkwonplatform.industrynewsInsight.service;

import com.sangkwon.sangkwonplatform.industrynewsInsight.dto.response.IndustryNewsInsightResponse;
import com.sangkwon.sangkwonplatform.industrynewsInsight.repository.IndustryNewsInsightRepository;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class IndustryNewsInsightService {

    private final IndustryNewsInsightRepository repository;
    private final MemberRepository memberRepository;

    public IndustryNewsInsightService(IndustryNewsInsightRepository repository, MemberRepository memberRepository){
        this.repository = repository;
        this.memberRepository = memberRepository;
    }

    // 업종·상권 동향은 Pro 전용. 비로그인은 SecurityConfig에서 막고, 여기선 Pro 여부로 402를 판정한다.
    public IndustryNewsInsightResponse getLatestInsight(Long memberId, String indutyCd) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 정보를 찾을 수 없습니다"));
        if (!member.isPro()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "업종·상권 동향은 Pro 전용이에요. Pro로 업그레이드하면 이용할 수 있어요.");
        }

        return repository.findFirstByIndutyCdOrderByYearMonthDesc(indutyCd)
                .map(entity -> IndustryNewsInsightResponse.from(entity))
                .orElse(new IndustryNewsInsightResponse(
                        indutyCd,
                        null,
                        null,
                        "아직 생성된 인사이트가 없습니다.",
                        0
                ));
    }
}
