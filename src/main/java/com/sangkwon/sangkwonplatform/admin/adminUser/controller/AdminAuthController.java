package com.sangkwon.sangkwonplatform.admin.adminUser.controller;

import com.sangkwon.sangkwonplatform.admin.adminUser.dto.request.AdminLoginRequest;
import com.sangkwon.sangkwonplatform.admin.adminUser.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.adminUser.service.AdminUserService;
import com.sangkwon.sangkwonplatform.admin.adminUser.session.SessionConst;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminUserService adminUserService;

    @PostMapping("/login")
    public ApiResponse<AdminSession> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AdminSession adminSession = adminUserService.login(request);

        // 세션 고정 방지: 로그인 성공 시 세션 ID를 회전시킨 뒤 인증 정보를 담는다
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        httpRequest.getSession().setAttribute(SessionConst.LOGIN_ADMIN, adminSession);

        return ApiResponse.ok(adminSession);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<AdminSession> me(
            @SessionAttribute(name = SessionConst.LOGIN_ADMIN, required = false)
            AdminSession adminSession
    ) {
        if (adminSession == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return ApiResponse.ok(adminSession);
    }
}
