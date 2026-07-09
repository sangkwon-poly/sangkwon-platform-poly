package com.sangkwon.sangkwonplatform.admin.notice.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.notice.dto.request.NoticeCreateRequest;
import com.sangkwon.sangkwonplatform.admin.notice.dto.request.NoticeStatusUpdateRequest;
import com.sangkwon.sangkwonplatform.admin.notice.dto.request.NoticeUpdateRequest;
import com.sangkwon.sangkwonplatform.admin.notice.dto.response.NoticeAdminDetailResponse;
import com.sangkwon.sangkwonplatform.admin.notice.dto.response.NoticePageResponse;
import com.sangkwon.sangkwonplatform.admin.notice.service.NoticeService;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.service.AdminAuditService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// 공지사항 관리: 목록·상세는 로그인 관리자면 열람, 작성·수정·상태변경·삭제는 OPERATOR 이상.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notices")
public class NoticeAdminController {

    private static final int MAX_SIZE = 100;

    private final NoticeService noticeService;
    private final AdminAuditService auditService;

    @GetMapping
    public ApiResponse<NoticePageResponse> list(@LoginAdmin AdminSession admin,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return ApiResponse.ok(NoticePageResponse.from(noticeService.getAdminList(pageable)));
    }

    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeAdminDetailResponse> detail(@LoginAdmin AdminSession admin,
                                                         @PathVariable Long noticeId) {
        return ApiResponse.ok(noticeService.getAdminDetail(noticeId));
    }

    @PostMapping
    public ApiResponse<Long> create(@LoginAdmin AdminSession admin,
                                    @Valid @RequestBody NoticeCreateRequest request,
                                    HttpServletRequest http) {
        requireManager(admin);
        Long id = noticeService.create(admin.adminId(), request);
        auditService.record(admin.adminId(), AuditAction.NOTICE_CREATE, "NOTICE",
                String.valueOf(id), null, http);
        return ApiResponse.ok(id);
    }

    @PutMapping("/{noticeId}")
    public ApiResponse<Void> update(@LoginAdmin AdminSession admin,
                                    @PathVariable Long noticeId,
                                    @Valid @RequestBody NoticeUpdateRequest request,
                                    HttpServletRequest http) {
        requireManager(admin);
        noticeService.update(noticeId, request);
        auditService.record(admin.adminId(), AuditAction.NOTICE_UPDATE, "NOTICE",
                String.valueOf(noticeId), null, http);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{noticeId}/status")
    public ApiResponse<Void> changeStatus(@LoginAdmin AdminSession admin,
                                          @PathVariable Long noticeId,
                                          @Valid @RequestBody NoticeStatusUpdateRequest request,
                                          HttpServletRequest http) {
        requireManager(admin);
        noticeService.changeStatus(noticeId, request.status());
        auditService.record(admin.adminId(), AuditAction.NOTICE_UPDATE, "NOTICE",
                String.valueOf(noticeId), "status=" + request.status(), http);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{noticeId}")
    public ApiResponse<Void> delete(@LoginAdmin AdminSession admin,
                                    @PathVariable Long noticeId,
                                    HttpServletRequest http) {
        requireManager(admin);
        noticeService.delete(noticeId);
        auditService.record(admin.adminId(), AuditAction.NOTICE_DELETE, "NOTICE",
                String.valueOf(noticeId), null, http);
        return ApiResponse.ok(null);
    }

    // 공지 작성·수정·삭제는 VIEWER를 제외한 관리자(OPERATOR 이상)만 가능
    private void requireManager(AdminSession admin) {
        if (admin.role() == AdminRole.VIEWER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "공지 관리 권한이 없습니다.");
        }
    }
}
