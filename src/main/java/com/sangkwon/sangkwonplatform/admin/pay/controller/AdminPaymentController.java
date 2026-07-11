package com.sangkwon.sangkwonplatform.admin.pay.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.service.AdminAuditService;
import com.sangkwon.sangkwonplatform.admin.pay.dto.request.PaymentCancelRequest;
import com.sangkwon.sangkwonplatform.admin.pay.dto.response.AdminPaymentResponse;
import com.sangkwon.sangkwonplatform.admin.pay.dto.response.PaymentPageResponse;
import com.sangkwon.sangkwonplatform.admin.pay.dto.response.PaymentSummaryResponse;
import com.sangkwon.sangkwonplatform.admin.pay.service.AdminPaymentService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// 관리자 결제·구독 조회. 매출이 걸린 데이터라 다른 운영 지표와 같이 SUPER_ADMIN 전용.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private static final int MAX_SIZE = 100;

    private final AdminPaymentService adminPaymentService;
    private final AdminAuditService auditService;

    // 주문 목록은 최신순 고정. 같은 시각 생성분이 페이지 경계에서 겹치지 않게 orderId를 2차 정렬로 둔다.
    @GetMapping
    public ApiResponse<PaymentPageResponse> getOrders(@LoginAdmin AdminSession admin,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) PaymentStatus status,
                                                      @RequestParam(required = false) BillingCycle cycle,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        requireSuperAdmin(admin);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt", "orderId"));
        return ApiResponse.ok(adminPaymentService.getOrders(keyword, status, cycle, pageable));
    }

    @GetMapping("/summary")
    public ApiResponse<PaymentSummaryResponse> getSummary(@LoginAdmin AdminSession admin) {
        requireSuperAdmin(admin);
        return ApiResponse.ok(adminPaymentService.getSummary());
    }

    // 환불(토스 결제취소 연동). 돈과 직결되는 조치라 SUPER_ADMIN 전용이고 사유를 감사 로그에 남긴다.
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<AdminPaymentResponse> cancel(@LoginAdmin AdminSession admin,
                                                    @PathVariable String orderId,
                                                    @Valid @RequestBody PaymentCancelRequest request,
                                                    HttpServletRequest http) {
        requireSuperAdmin(admin);
        AdminPaymentResponse res = adminPaymentService.cancel(orderId, request.reason());
        auditService.record(admin.adminId(), AuditAction.PAYMENT_CANCEL, "PAYMENT_ORDER",
                orderId, "reason=" + request.reason(), http);
        return ApiResponse.ok(res);
    }

    private void requireSuperAdmin(AdminSession admin) {
        if (admin.role() != AdminRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SUPER_ADMIN 권한이 필요합니다.");
        }
    }
}
