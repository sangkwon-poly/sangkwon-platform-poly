package com.sangkwon.sangkwonplatform.admin.inquiry.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.request.InquiryAnswerRequest;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.response.InquiryAdminDetailResponse;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.response.InquiryPageResponse;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;
import com.sangkwon.sangkwonplatform.admin.inquiry.service.InquiryService;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.service.AdminAuditService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// 1:1 문의 관리: 목록·상세는 로그인 관리자면 열람, 답변·닫기는 OPERATOR 이상.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inquiries")
public class InquiryAdminController {

    private static final int MAX_SIZE = 100;

    private final InquiryService inquiryService;
    private final AdminAuditService auditService;

    @GetMapping
    public ApiResponse<InquiryPageResponse> list(@LoginAdmin AdminSession admin,
                                                 @RequestParam(required = false) InquiryStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok(InquiryPageResponse.from(inquiryService.getAdminList(status, pageable)));
    }

    @GetMapping("/{inquiryId}")
    public ApiResponse<InquiryAdminDetailResponse> detail(@LoginAdmin AdminSession admin,
                                                          @PathVariable Long inquiryId) {
        return ApiResponse.ok(inquiryService.getAdminDetail(inquiryId));
    }

    @PostMapping("/{inquiryId}/answer")
    public ApiResponse<InquiryAdminDetailResponse> answer(@LoginAdmin AdminSession admin,
                                                          @PathVariable Long inquiryId,
                                                          @Valid @RequestBody InquiryAnswerRequest request,
                                                          HttpServletRequest http) {
        requireManager(admin);
        InquiryAdminDetailResponse res = inquiryService.answer(admin.adminId(), inquiryId, request);
        auditService.record(admin.adminId(), AuditAction.INQUIRY_ANSWER, "INQUIRY",
                String.valueOf(inquiryId), null, http);
        return ApiResponse.ok(res);
    }

    @PatchMapping("/{inquiryId}/close")
    public ApiResponse<Void> close(@LoginAdmin AdminSession admin,
                                   @PathVariable Long inquiryId,
                                   HttpServletRequest http) {
        requireManager(admin);
        inquiryService.close(inquiryId);
        auditService.record(admin.adminId(), AuditAction.INQUIRY_CLOSE, "INQUIRY",
                String.valueOf(inquiryId), null, http);
        return ApiResponse.ok(null);
    }

    // 답변·닫기는 VIEWER를 제외한 관리자(OPERATOR 이상)만 가능
    private void requireManager(AdminSession admin) {
        if (admin.role() == AdminRole.VIEWER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "문의 관리 권한이 없습니다.");
        }
    }
}
