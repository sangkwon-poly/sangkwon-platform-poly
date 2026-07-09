package com.sangkwon.sangkwonplatform.admin.support.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.service.AdminAuditService;
import com.sangkwon.sangkwonplatform.admin.support.dto.request.VisibilityUpdateRequest;
import com.sangkwon.sangkwonplatform.admin.support.dto.response.AdminSupportCountsResponse;
import com.sangkwon.sangkwonplatform.admin.support.dto.response.AdminSupportPageResponse;
import com.sangkwon.sangkwonplatform.admin.support.service.AdminSupportService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// 지원사업 관리: 숨김 포함 목록·요약·노출 전환. SUPER_ADMIN 전용.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/support-programs")
public class AdminSupportController {

    private final AdminSupportService adminSupportService;
    private final AdminAuditService auditService;

    @GetMapping
    public ApiResponse<AdminSupportPageResponse> list(@LoginAdmin AdminSession admin,
                                                      @RequestParam(required = false) String visibility,
                                                      @RequestParam(required = false) String source,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(adminSupportService.search(visibility, source, type, keyword, page, size));
    }

    @GetMapping("/counts")
    public ApiResponse<AdminSupportCountsResponse> counts(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(adminSupportService.counts());
    }

    @PatchMapping("/{sourceCd}/{programId}/visibility")
    public ApiResponse<Void> updateVisibility(@LoginAdmin AdminSession admin,
                                              @PathVariable String sourceCd,
                                              @PathVariable String programId,
                                              @RequestBody VisibilityUpdateRequest request,
                                              HttpServletRequest http) {
        requireSuperAdmin(admin);
        adminSupportService.setVisibility(sourceCd, programId, request.visible());
        auditService.record(admin.adminId(), AuditAction.SUPPORT_VISIBILITY_UPDATE, "SUPPORT_PROGRAM",
                sourceCd + "/" + programId, "visible=" + request.visible(), http);
        return ApiResponse.ok(null);
    }

    private void requireSuperAdmin(AdminSession admin) {
        if (admin.role() != AdminRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SUPER_ADMIN 권한이 필요합니다.");
        }
    }
}
