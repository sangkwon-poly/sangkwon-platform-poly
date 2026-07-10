package com.sangkwon.sangkwonplatform.admin.account.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.request.*;
import com.sangkwon.sangkwonplatform.admin.account.dto.response.AdminListResponse;
import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.service.AdminUserService;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.account.session.SessionConst;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.service.AdminAuditService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/admin-users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminAuditService auditService;

    @PostMapping
    public ApiResponse<Void> join(@LoginAdmin AdminSession admin,
                                  @Valid @RequestBody AdminJoinRequest request,
                                  HttpServletRequest http) {
        requireSuperAdmin(admin);
        adminUserService.join(request);
        auditService.record(admin.adminId(), AuditAction.ADMIN_CREATE, "ADMIN",
                request.loginId(), "role=" + request.role(), http);
        return ApiResponse.ok(null);
    }

    @GetMapping
    public ApiResponse<List<AdminListResponse>> getAdminList(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(adminUserService.getAdminList());
    }

    @PatchMapping("/{adminId}/name")
    public ApiResponse<Void> updateName(@LoginAdmin AdminSession admin,
                                        @PathVariable Long adminId,
                                        @Valid @RequestBody AdminNameUpdateRequest request,
                                        HttpServletRequest http) {
        requireSelf(admin, adminId);
        adminUserService.updateName(admin.adminId(), request);
        auditService.record(admin.adminId(), AuditAction.NAME_UPDATE, "ADMIN",
                String.valueOf(admin.adminId()), null, http);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{adminId}/password")
    public ApiResponse<Void> updatePassword(@LoginAdmin AdminSession admin,
                                            @PathVariable Long adminId,
                                            @Valid @RequestBody AdminPasswordUpdateRequest request,
                                            HttpServletRequest http) {
        requireSelf(admin, adminId);
        AdminSession updated = adminUserService.updatePassword(admin.adminId(), request);
        // 방금 올라간 pwVersion으로 본인 세션 스냅샷을 갱신해, 다른 세션만 끊기고 본인은 로그아웃되지 않게 한다
        http.getSession().setAttribute(SessionConst.LOGIN_ADMIN, updated);
        auditService.record(admin.adminId(), AuditAction.PASSWORD_CHANGE, "ADMIN",
                String.valueOf(admin.adminId()), null, http);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{adminId}/reset-password")
    public ApiResponse<Void> resetPassword(@LoginAdmin AdminSession admin,
                                           @PathVariable Long adminId,
                                           @Valid @RequestBody AdminPasswordResetRequest request,
                                           HttpServletRequest http) {
        requireSuperAdmin(admin);
        requireNotSelf(admin, adminId);
        adminUserService.resetPassword(adminId, request);
        auditService.record(admin.adminId(), AuditAction.PASSWORD_RESET, "ADMIN",
                String.valueOf(adminId), null, http);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{adminId}/role")
    public ApiResponse<Void> updateRole(@LoginAdmin AdminSession admin,
                                        @PathVariable Long adminId,
                                        @Valid @RequestBody AdminRoleUpdateRequest request,
                                        HttpServletRequest http) {
        requireSuperAdmin(admin);
        requireNotSelf(admin, adminId);
        adminUserService.updateRole(adminId, request);
        auditService.record(admin.adminId(), AuditAction.ADMIN_ROLE_UPDATE, "ADMIN",
                String.valueOf(adminId), "role=" + request.role(), http);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{adminId}/status")
    public ApiResponse<Void> updateStatus(@LoginAdmin AdminSession admin,
                                          @PathVariable Long adminId,
                                          @Valid @RequestBody AdminStatusUpdateRequest request,
                                          HttpServletRequest http) {
        requireSuperAdmin(admin);
        requireNotSelf(admin, adminId);
        adminUserService.updateStatus(adminId, request);
        auditService.record(admin.adminId(), AuditAction.ADMIN_STATUS_UPDATE, "ADMIN",
                String.valueOf(adminId), "status=" + request.status(), http);
        return ApiResponse.ok(null);
    }

    private void requireSuperAdmin(AdminSession admin) {
        if (admin.role() != AdminRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SUPER_ADMIN 권한이 필요합니다.");
        }
    }

    private void requireSelf(AdminSession admin, Long targetAdminId) {
        if (!admin.adminId().equals(targetAdminId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 계정만 수정할 수 있습니다.");
        }
    }

    // 본인 계정의 권한·상태를 스스로 바꿔 잠기거나 권한을 잃는 사고를 막는다
    private void requireNotSelf(AdminSession admin, Long targetAdminId) {
        if (admin.adminId().equals(targetAdminId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인 계정의 권한·상태는 변경할 수 없습니다.");
        }
    }
}
