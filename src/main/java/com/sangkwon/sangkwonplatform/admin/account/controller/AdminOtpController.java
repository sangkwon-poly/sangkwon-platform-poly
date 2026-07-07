package com.sangkwon.sangkwonplatform.admin.account.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.request.OtpEnableRequest;
import com.sangkwon.sangkwonplatform.admin.account.dto.response.OtpSetupResponse;
import com.sangkwon.sangkwonplatform.admin.account.dto.response.OtpStatusResponse;
import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.otp.QrCodes;
import com.sangkwon.sangkwonplatform.admin.account.service.AdminUserService;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.service.AdminAuditService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 관리자 본인의 2단계 인증(OTP) 설정. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth/otp")
public class AdminOtpController {

    private final AdminUserService adminUserService;
    private final AdminAuditService auditService;

    @GetMapping("/status")
    public ApiResponse<OtpStatusResponse> status(@LoginAdmin AdminSession admin) {
        return ApiResponse.ok(new OtpStatusResponse(adminUserService.isOtpEnabled(admin.adminId())));
    }

    @PostMapping("/setup")
    public ApiResponse<OtpSetupResponse> setup(@LoginAdmin AdminSession admin) {
        return ApiResponse.ok(adminUserService.setupOtp(admin.adminId()));
    }

    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qr(@LoginAdmin AdminSession admin) {
        return QrCodes.pngBytes(adminUserService.otpauthUrlFor(admin.adminId()), 240);
    }

    @PostMapping("/enable")
    public ApiResponse<Void> enable(@LoginAdmin AdminSession admin,
                                    @Valid @RequestBody OtpEnableRequest request,
                                    HttpServletRequest http) {
        adminUserService.enableOtp(admin.adminId(), request.otp());
        auditService.record(admin.adminId(), AuditAction.OTP_ENABLE, "ADMIN",
                String.valueOf(admin.adminId()), null, http);
        return ApiResponse.ok(null);
    }

    @PostMapping("/disable")
    public ApiResponse<Void> disable(@LoginAdmin AdminSession admin, HttpServletRequest http) {
        adminUserService.disableOtp(admin.adminId());
        auditService.record(admin.adminId(), AuditAction.OTP_DISABLE, "ADMIN",
                String.valueOf(admin.adminId()), null, http);
        return ApiResponse.ok(null);
    }
}
