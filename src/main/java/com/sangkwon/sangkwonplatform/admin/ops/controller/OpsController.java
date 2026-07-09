package com.sangkwon.sangkwonplatform.admin.ops.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.ops.dto.ApiUsageResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.AuditPageResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.BatchCatalogResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.BatchLogResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.OverviewResponse;
import com.sangkwon.sangkwonplatform.admin.ops.service.BatchAdminService;
import com.sangkwon.sangkwonplatform.admin.ops.service.OpsService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 운영 대시보드 조회: 배치 적재 이력, 외부 API 사용량, 관리자 감사 로그. SUPER_ADMIN 전용. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/ops")
public class OpsController {

    private final OpsService opsService;
    private final BatchAdminService batchAdminService;

    @GetMapping("/overview")
    public ApiResponse<OverviewResponse> overview(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(opsService.overview());
    }

    @GetMapping("/batch")
    public ApiResponse<List<BatchLogResponse>> batch(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(opsService.recentBatches(100));
    }

    // 적재 카탈로그: 데이터셋 목록 + 각 데이터셋 최근 상태
    @GetMapping("/batch/catalog")
    public ApiResponse<List<BatchCatalogResponse>> batchCatalog(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(batchAdminService.catalog());
    }

    // 데이터셋 상세: 최근 실행 이력
    @GetMapping("/batch/{code}/history")
    public ApiResponse<List<BatchLogResponse>> batchHistory(@LoginAdmin AdminSession admin,
                                                            @PathVariable String code) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(batchAdminService.history(code));
    }

    // 앱 적재 트리거(비동기). 진행 상황은 카탈로그 폴링으로 확인한다.
    @PostMapping("/batch/{code}/run")
    public ApiResponse<Void> batchRun(@LoginAdmin AdminSession admin,
                                      @PathVariable String code,
                                      HttpServletRequest request) {
        requireSuperAdmin(admin);
        batchAdminService.run(code, admin, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api-usage")
    public ApiResponse<List<ApiUsageResponse>> apiUsage(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(opsService.todayApiUsage());
    }

    @GetMapping("/audit")
    public ApiResponse<AuditPageResponse> audit(@LoginAdmin AdminSession admin,
                                                @RequestParam(required = false) String action,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "30") int size) {
        requireSuperAdmin(admin);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok(opsService.searchAudits(action, pageable));
    }

    private void requireSuperAdmin(AdminSession admin) {
        if (admin.role() != AdminRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SUPER_ADMIN 권한이 필요합니다.");
        }
    }
}
