package com.sangkwon.sangkwonplatform.admin.account.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.request.AdminLoginRequest;
import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.otp.OtpRequiredException;
import com.sangkwon.sangkwonplatform.admin.account.service.AdminUserService;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.account.session.SessionConst;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.service.AdminAuditService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminUserService adminUserService;
    private final AdminAuditService auditService;

    @PostMapping("/login")
    public ApiResponse<AdminSession> login(@Valid @RequestBody AdminLoginRequest request,
                                           HttpServletRequest httpRequest) {
        AdminSession adminSession = adminUserService.login(request);

        // 세션 고정 방지: 로그인 성공 시 세션 ID를 회전시킨 뒤 인증 정보를 담는다
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        httpRequest.getSession().setAttribute(SessionConst.LOGIN_ADMIN, adminSession);

        auditService.record(adminSession.adminId(), AuditAction.LOGIN, null, null, null, httpRequest);
        return ApiResponse.ok(adminSession);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<AdminSession> me(@LoginAdmin AdminSession admin) {
        return ApiResponse.ok(admin);
    }

    // 비번은 맞았고 2단계 인증 코드만 필요한 경우: 401 + code=OTP_REQUIRED 로 프론트에 OTP 단계 전환을 알린다
    @ExceptionHandler(OtpRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleOtpRequired(OtpRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("OTP_REQUIRED", "OTP 인증코드를 입력하세요."));
    }
}
