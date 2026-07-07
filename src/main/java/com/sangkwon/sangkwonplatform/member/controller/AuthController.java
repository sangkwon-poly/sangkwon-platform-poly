package com.sangkwon.sangkwonplatform.member.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberLoginRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.TokenResponse;
import com.sangkwon.sangkwonplatform.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody MemberLoginRequest req) {
        String token = memberService.login(req);
        return ApiResponse.ok(new TokenResponse(token));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // JWT: 클라이언트가 토큰 폐기
        return ApiResponse.<Void>ok(null);
    }
}
