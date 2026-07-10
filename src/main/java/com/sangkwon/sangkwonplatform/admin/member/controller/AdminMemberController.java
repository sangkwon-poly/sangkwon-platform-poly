package com.sangkwon.sangkwonplatform.admin.member.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.member.dto.request.MemberStatusUpdateRequest;
import com.sangkwon.sangkwonplatform.admin.member.dto.response.AdminMemberResponse;
import com.sangkwon.sangkwonplatform.admin.member.dto.response.MemberCountsResponse;
import com.sangkwon.sangkwonplatform.admin.member.dto.response.MemberPageResponse;
import com.sangkwon.sangkwonplatform.admin.member.service.AdminMemberService;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.service.AdminAuditService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.member.entity.MemberStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

// 관리자 회원 관리: 목록·검색·상태변경. SUPER_ADMIN 전용.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private static final int MAX_SIZE = 100;

    // 목록 정렬 허용 컬럼. 그 외 값은 조용히 기본 정렬로 떨어뜨린다(내부 관리 UI라 400 대신 폴백).
    private static final Set<String> SORTABLE = Set.of("createdAt", "lastLoginAt", "loginId");

    private final AdminMemberService adminMemberService;
    private final AdminAuditService auditService;

    @GetMapping
    public ApiResponse<MemberPageResponse> getMembers(@LoginAdmin AdminSession admin,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) MemberStatus status,
                                                      @RequestParam(required = false) String sort,
                                                      @RequestParam(required = false) String direction,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        requireSuperAdmin(admin);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, resolveSort(sort, direction));
        Page<AdminMemberResponse> result = adminMemberService.getMembers(keyword, status, pageable);
        return ApiResponse.ok(MemberPageResponse.from(result));
    }

    // 정렬 파라미터를 화이트리스트로만 받는다. 미허용/누락은 createdAt 내림차순.
    // 정렬값이 같은 행이 페이지 경계에서 겹치거나 빠지지 않게 memberId를 2차 정렬로 고정한다.
    private Sort resolveSort(String sort, String direction) {
        String property = SORTABLE.contains(sort) ? sort : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(new Sort.Order(dir, property), new Sort.Order(dir, "memberId"));
    }

    @GetMapping("/counts")
    public ApiResponse<MemberCountsResponse> getCounts(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(adminMemberService.getCounts());
    }

    @PatchMapping("/{memberId}/status")
    public ApiResponse<AdminMemberResponse> updateStatus(@LoginAdmin AdminSession admin,
                                                         @PathVariable Long memberId,
                                                         @Valid @RequestBody MemberStatusUpdateRequest request,
                                                         HttpServletRequest http) {
        requireSuperAdmin(admin);
        AdminMemberService.StatusChange change = adminMemberService.changeStatus(memberId, request.status());
        auditService.record(admin.adminId(), AuditAction.MEMBER_STATUS_UPDATE, "MEMBER",
                String.valueOf(memberId), "from=" + change.from() + ",to=" + request.status(), http);
        return ApiResponse.ok(change.member());
    }

    private void requireSuperAdmin(AdminSession admin) {
        if (admin.role() != AdminRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SUPER_ADMIN 권한이 필요합니다.");
        }
    }
}
