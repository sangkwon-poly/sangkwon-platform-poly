package com.sangkwon.sangkwonplatform.admin.adminUser.controller;

import com.sangkwon.sangkwonplatform.admin.adminUser.dto.request.*;
import com.sangkwon.sangkwonplatform.admin.adminUser.dto.response.AdminListResponse;
import com.sangkwon.sangkwonplatform.admin.adminUser.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/admin-users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    public void join(@Valid @RequestBody AdminJoinRequest request) throws IllegalAccessException {
        adminUserService.join(request);
    }

    @GetMapping
    public List<AdminListResponse> getAdminList(){
        return adminUserService.getAdminList();
    }


    @PatchMapping("/{adminId}/name")
    public void updateName(
            @PathVariable Long adminId,
            @Valid @RequestBody AdminNameUpdateRequest request
            ) {
        adminUserService.updateName(adminId, request);
    }

    @PatchMapping("/{adminId}/password")
    public void updatePassword(
            @PathVariable Long adminId,
            @Valid @RequestBody AdminPasswordUpdateRequest request
            ) {
        adminUserService.updatePassword(adminId, request);
    }

    @PatchMapping("/{adminId}/role")
    public void updateRole(
            @PathVariable Long adminId,
            @Valid @RequestBody AdminRoleUpdateRequest request
            ) {
        adminUserService.updateRole(adminId, request);
    }

    @PatchMapping("/{adminId}/status")
    public void updateStatus(
            @PathVariable Long adminId,
            @Valid @RequestBody AdminStatusUpdateRequest request
    ) {
        adminUserService.updateStatus(adminId, request);
    }
}
