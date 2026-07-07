package com.sangkwon.sangkwonplatform.admin.adminUser.controller;

import com.sangkwon.sangkwonplatform.admin.adminUser.dto.request.AdminLoginRequest;
import com.sangkwon.sangkwonplatform.admin.adminUser.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.adminUser.service.AdminUserService;
import com.sangkwon.sangkwonplatform.admin.adminUser.session.SessionConst;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminUserService adminUserService;

    @PostMapping("/login")
    public AdminSession login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AdminSession adminSession = adminUserService.login(request);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SessionConst.LOGIN_ADMIN, adminSession);

        return adminSession;
    }

    @PostMapping("/logout")
    public void logout(HttpSession session) {
        session.invalidate();
    }

    @GetMapping("/me")
    public AdminSession me(
            @SessionAttribute(name = SessionConst.LOGIN_ADMIN, required = false)
            AdminSession adminSession
    ) {
        if (adminSession == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        return adminSession;
    }
}