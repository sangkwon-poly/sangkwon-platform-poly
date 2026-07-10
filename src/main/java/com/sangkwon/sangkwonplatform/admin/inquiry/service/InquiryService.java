package com.sangkwon.sangkwonplatform.admin.inquiry.service;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.request.InquiryAnswerRequest;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.request.InquiryCreateRequest;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.response.InquiryAdminDetailResponse;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.response.InquiryAdminSummaryResponse;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.response.InquiryUserAnswerResponse;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.response.InquiryUserListResponse;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;
import com.sangkwon.sangkwonplatform.admin.inquiry.repository.InquiryRepository;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final AdminUserRepository adminUserRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public Page<InquiryAdminSummaryResponse> getAdminList(InquiryStatus status, Pageable pageable) {
        return inquiryRepository.searchForAdmin(status, pageable).map(InquiryAdminSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public InquiryAdminDetailResponse getAdminDetail(Long inquiryId) {
        return InquiryAdminDetailResponse.from(find(inquiryId));
    }

    // 답변 등록(OPEN → ANSWERED). 답변자·답변시각을 함께 채워 CK_INQ_ANSWERED를 만족시킨다.
    public InquiryAdminDetailResponse answer(Long adminId, Long inquiryId, InquiryAnswerRequest req) {
        Inquiry inquiry = find(inquiryId);
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "관리자를 찾을 수 없습니다."));
        inquiry.answerBy(admin, req.answer());
        return InquiryAdminDetailResponse.from(inquiry);
    }

    public void close(Long inquiryId) {
        find(inquiryId).close();
    }

    // 회원 문의 등록. 접수(OPEN) 상태로 시작한다.
    public Long create(Long memberId, InquiryCreateRequest req) {
        Member member = memberRepository.findById(requireLogin(memberId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
        Inquiry inquiry = new Inquiry();
        inquiry.setMember(member);
        inquiry.setTitle(req.title());
        inquiry.setContent(req.content());
        return inquiryRepository.save(inquiry).getInquiryId();
    }

    @Transactional(readOnly = true)
    public Page<InquiryUserListResponse> getMyList(Long memberId, Pageable pageable) {
        return inquiryRepository.findByMemberMemberIdOrderByCreatedAtDesc(requireLogin(memberId), pageable)
                .map(InquiryUserListResponse::from);
    }

    // 본인 문의만 열람. 남의 문의는 존재 여부도 숨기려 403 대신 404로 답한다.
    @Transactional(readOnly = true)
    public InquiryUserAnswerResponse getMyDetail(Long memberId, Long inquiryId) {
        requireLogin(memberId);
        Inquiry inquiry = find(inquiryId);
        if (inquiry.getMember() == null || !inquiry.getMember().getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다.");
        }
        return InquiryUserAnswerResponse.from(inquiry);
    }

    private Long requireLogin(Long memberId) {
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return memberId;
    }

    private Inquiry find(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."));
    }
}
