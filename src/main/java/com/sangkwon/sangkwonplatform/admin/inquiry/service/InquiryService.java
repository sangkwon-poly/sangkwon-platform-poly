package com.sangkwon.sangkwonplatform.admin.inquiry.service;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.request.InquiryAnswerRequest;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.response.InquiryAdminDetailResponse;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.response.InquiryAdminSummaryResponse;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;
import com.sangkwon.sangkwonplatform.admin.inquiry.repository.InquiryRepository;
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

    private Inquiry find(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."));
    }
}
