package com.sangkwon.sangkwonplatform.admin.account.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.request.AdminLoginRequest;
import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.otp.OtpRequiredException;
import com.sangkwon.sangkwonplatform.admin.account.security.ClientIpResolver;
import com.sangkwon.sangkwonplatform.admin.account.security.TrustedDeviceService;
import com.sangkwon.sangkwonplatform.admin.account.service.AdminUserService;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.account.session.SessionConst;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.service.AdminAuditService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminUserService adminUserService;
    private final AdminAuditService auditService;
    private final TrustedDeviceService trustedDeviceService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/login")
    public ApiResponse<AdminSession> login(@Valid @RequestBody AdminLoginRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        String trustToken = readTrustToken(httpRequest);
        AdminSession adminSession = adminUserService.login(request, trustToken, clientIpResolver.resolve(httpRequest));

        // 세션 고정 방지: 로그인 성공 시 세션 ID를 회전시킨 뒤 인증 정보를 담는다
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        httpRequest.getSession().setAttribute(SessionConst.LOGIN_ADMIN, adminSession);

        // OTP가 활성화된 계정에서 인증을 마친 경우에만 현재 OTP 상태에 묶인 신뢰 쿠키를 발급한다.
        // OTP가 꺼져 있거나 사용자가 체크를 해제하면 예전에 남은 쿠키도 즉시 지운다.
        String newTrustToken = request.trustDevice()
                ? adminUserService.issueTrustToken(adminSession.adminId())
                : null;
        if (newTrustToken != null) {
            ResponseCookie cookie = ResponseCookie.from(TrustedDeviceService.COOKIE_NAME, newTrustToken)
                    .httpOnly(true)
                    .secure(httpRequest.isSecure())
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofDays(trustedDeviceService.days()))
                    .build();
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        } else {
            ResponseCookie clearCookie = ResponseCookie.from(TrustedDeviceService.COOKIE_NAME, "")
                    .httpOnly(true)
                    .secure(httpRequest.isSecure())
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ZERO)
                    .build();
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
        }

        auditService.record(adminSession.adminId(), AuditAction.LOGIN, null, null, null, httpRequest);
        return ApiResponse.ok(adminSession);
    }

    private String readTrustToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (TrustedDeviceService.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            Object attr = session.getAttribute(SessionConst.LOGIN_ADMIN);
            if (attr instanceof AdminSession admin) {
                auditService.record(admin.adminId(), AuditAction.LOGOUT, null, null, null, http);
            }
            session.invalidate();
        }
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
