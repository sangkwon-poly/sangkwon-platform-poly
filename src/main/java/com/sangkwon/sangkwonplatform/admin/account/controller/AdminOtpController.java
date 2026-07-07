package com.sangkwon.sangkwonplatform.admin.account.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.request.OtpEnableRequest;
import com.sangkwon.sangkwonplatform.admin.account.dto.response.OtpSetupResponse;
import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.service.AdminUserService;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인한 관리자 본인의 2단계 인증(OTP) 설정. 세션 필수(AdminAuthInterceptor).
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth/otp")
public class AdminOtpController {

    private final AdminUserService adminUserService;

    // 1) 설정 시작: 비밀키 발급 → 인증 앱에 등록(QR)
    @PostMapping("/setup")
    public ApiResponse<OtpSetupResponse> setup(@LoginAdmin AdminSession admin) {
        return ApiResponse.ok(adminUserService.setupOtp(admin.adminId()));
    }

    // 2) 앱이 만든 코드로 확인 → 2FA 활성화
    @PostMapping("/enable")
    public ApiResponse<Void> enable(@LoginAdmin AdminSession admin,
                                    @Valid @RequestBody OtpEnableRequest request) {
        adminUserService.enableOtp(admin.adminId(), request.otp());
        return ApiResponse.ok(null);
    }

    // 3) 해제
    @PostMapping("/disable")
    public ApiResponse<Void> disable(@LoginAdmin AdminSession admin) {
        adminUserService.disableOtp(admin.adminId());
        return ApiResponse.ok(null);
    }
}
