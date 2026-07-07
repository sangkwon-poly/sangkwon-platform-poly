package com.sangkwon.sangkwonplatform.admin.ops.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.ops.dto.ApiUsageResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.AuditLogResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.BatchLogResponse;
import com.sangkwon.sangkwonplatform.admin.ops.service.OpsService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 운영 대시보드 조회: 배치 적재 이력, 외부 API 사용량, 관리자 감사 로그. SUPER_ADMIN 전용. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/ops")
public class OpsController {

    private final OpsService opsService;

    @GetMapping("/batch")
    public ApiResponse<List<BatchLogResponse>> batch(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(opsService.recentBatches(100));
    }

    @GetMapping("/api-usage")
    public ApiResponse<List<ApiUsageResponse>> apiUsage(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(opsService.todayApiUsage());
    }

    @GetMapping("/audit")
    public ApiResponse<List<AuditLogResponse>> audit(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(opsService.recentAudits(100));
    }

    private void requireSuperAdmin(AdminSession admin) {
        if (admin.role() != AdminRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SUPER_ADMIN 권한이 필요합니다.");
        }
    }
}
