package com.sangkwon.sangkwonplatform.admin.account.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.request.*;
import com.sangkwon.sangkwonplatform.admin.account.dto.response.AdminListResponse;
import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.service.AdminUserService;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
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
    public ApiResponse<Void> join(@LoginAdmin AdminSession admin,
                                  @Valid @RequestBody AdminJoinRequest request) {
        requireSuperAdmin(admin);
        adminUserService.join(request);
        return ApiResponse.ok(null);
    }

    // 관리자 목록 (SUPER_ADMIN 전용)
    @GetMapping
    public ApiResponse<List<AdminListResponse>> getAdminList(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(adminUserService.getAdminList());
    }

    // 이름 수정 (본인만)
    @PatchMapping("/{adminId}/name")
    public ApiResponse<Void> updateName(@LoginAdmin AdminSession admin,
                                        @PathVariable Long adminId,
                                        @Valid @RequestBody AdminNameUpdateRequest request) {
        requireSelf(admin, adminId);
        adminUserService.updateName(admin.adminId(), request);
        return ApiResponse.ok(null);
    }

    // 비밀번호 수정 (본인만)
    @PatchMapping("/{adminId}/password")
    public ApiResponse<Void> updatePassword(@LoginAdmin AdminSession admin,
                                            @PathVariable Long adminId,
                                            @Valid @RequestBody AdminPasswordUpdateRequest request) {
        requireSelf(admin, adminId);
        adminUserService.updatePassword(admin.adminId(), request);
        return ApiResponse.ok(null);
    }

    // 역할 변경 (SUPER_ADMIN 전용)
    @PatchMapping("/{adminId}/role")
    public ApiResponse<Void> updateRole(@LoginAdmin AdminSession admin,
                                        @PathVariable Long adminId,
                                        @Valid @RequestBody AdminRoleUpdateRequest request) {
        requireSuperAdmin(admin);
        adminUserService.updateRole(adminId, request);
        return ApiResponse.ok(null);
    }

    // 상태 변경 (SUPER_ADMIN 전용)
    @PatchMapping("/{adminId}/status")
    public ApiResponse<Void> updateStatus(@LoginAdmin AdminSession admin,
                                          @PathVariable Long adminId,
                                          @Valid @RequestBody AdminStatusUpdateRequest request) {
        requireSuperAdmin(admin);
        adminUserService.updateStatus(adminId, request);
        return ApiResponse.ok(null);
    }

    // 세션은 @LoginAdmin 리졸버가 보장(비로그인 401)하므로 여기서는 역할/본인 여부만 본다
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
}
