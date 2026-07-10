package com.sangkwon.sangkwonplatform.admin.inquiry.controller;

import com.sangkwon.sangkwonplatform.admin.inquiry.dto.request.InquiryCreateRequest;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.response.InquiryUserAnswerResponse;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.response.InquiryUserPageResponse;
import com.sangkwon.sangkwonplatform.admin.inquiry.service.InquiryService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 회원 1:1 문의: 등록·내 목록·내 상세. 비로그인이면 memberId가 null이라 서비스에서 401로 걸러진다.
// 답변·닫기 등 관리자 기능은 InquiryAdminController.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inquiries")
public class InquiryUserController {

    private static final int MAX_SIZE = 100;

    private final InquiryService inquiryService;

    @PostMapping
    public ApiResponse<Long> create(@AuthenticationPrincipal Long memberId,
                                    @Valid @RequestBody InquiryCreateRequest request) {
        return ApiResponse.ok(inquiryService.create(memberId, request));
    }

    @GetMapping("/my")
    public ApiResponse<InquiryUserPageResponse> myList(@AuthenticationPrincipal Long memberId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "5") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return ApiResponse.ok(InquiryUserPageResponse.from(inquiryService.getMyList(memberId, pageable)));
    }

    @GetMapping("/{inquiryId}")
    public ApiResponse<InquiryUserAnswerResponse> myDetail(@AuthenticationPrincipal Long memberId,
                                                           @PathVariable Long inquiryId) {
        return ApiResponse.ok(inquiryService.getMyDetail(memberId, inquiryId));
    }
}
