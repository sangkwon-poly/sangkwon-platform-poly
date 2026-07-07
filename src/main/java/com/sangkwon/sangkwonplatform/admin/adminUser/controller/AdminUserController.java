package com.sangkwon.sangkwonplatform.admin.adminUser.controller;

import com.sangkwon.sangkwonplatform.admin.adminUser.dto.request.*;
import com.sangkwon.sangkwonplatform.admin.adminUser.dto.response.AdminListResponse;
import com.sangkwon.sangkwonplatform.admin.adminUser.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.adminUser.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.adminUser.service.AdminUserService;
import com.sangkwon.sangkwonplatform.admin.adminUser.session.SessionConst;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
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

    // 관리자 생성 (SUPER_ADMIN 전용)
    @PostMapping
    public ApiResponse<Void> join(
            @SessionAttribute(name = SessionConst.LOGIN_ADMIN, required = false) AdminSession session,
            @Valid @RequestBody AdminJoinRequest request) {
        requireSuperAdmin(session);
        adminUserService.join(request);
        return ApiResponse.ok(null);
    }

    // 관리자 목록 (SUPER_ADMIN 전용)
    @GetMapping
    public ApiResponse<List<AdminListResponse>> getAdminList(
            @SessionAttribute(name = SessionConst.LOGIN_ADMIN, required = false) AdminSession session) {
        requireSuperAdmin(session);
        return ApiResponse.ok(adminUserService.getAdminList());
    }

    // 이름 수정 (본인만)
    @PatchMapping("/{adminId}/name")
    public ApiResponse<Void> updateName(
            @SessionAttribute(name = SessionConst.LOGIN_ADMIN, required = false) AdminSession session,
            @PathVariable Long adminId,
            @Valid @RequestBody AdminNameUpdateRequest request) {
        requireSelf(session, adminId);
        adminUserService.updateName(session.adminId(), request);
        return ApiResponse.ok(null);
    }

    // 비밀번호 수정 (본인만)
    @PatchMapping("/{adminId}/password")
    public ApiResponse<Void> updatePassword(
            @SessionAttribute(name = SessionConst.LOGIN_ADMIN, required = false) AdminSession session,
            @PathVariable Long adminId,
            @Valid @RequestBody AdminPasswordUpdateRequest request) {
        requireSelf(session, adminId);
        adminUserService.updatePassword(session.adminId(), request);
        return ApiResponse.ok(null);
    }

    // 역할 변경 (SUPER_ADMIN 전용)
    @PatchMapping("/{adminId}/role")
    public ApiResponse<Void> updateRole(
            @SessionAttribute(name = SessionConst.LOGIN_ADMIN, required = false) AdminSession session,
            @PathVariable Long adminId,
            @Valid @RequestBody AdminRoleUpdateRequest request) {
        requireSuperAdmin(session);
        adminUserService.updateRole(adminId, request);
        return ApiResponse.ok(null);
    }

    // 상태 변경 (SUPER_ADMIN 전용)
    @PatchMapping("/{adminId}/status")
    public ApiResponse<Void> updateStatus(
            @SessionAttribute(name = SessionConst.LOGIN_ADMIN, required = false) AdminSession session,
            @PathVariable Long adminId,
            @Valid @RequestBody AdminStatusUpdateRequest request) {
        requireSuperAdmin(session);
        adminUserService.updateStatus(adminId, request);
        return ApiResponse.ok(null);
    }

    private void requireSuperAdmin(AdminSession session) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (session.role() != AdminRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SUPER_ADMIN 권한이 필요합니다.");
        }
    }

    private void requireSelf(AdminSession session, Long targetAdminId) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (!session.adminId().equals(targetAdminId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 계정만 수정할 수 있습니다.");
        }
    }
}
