package com.sangkwon.sangkwonplatform.member.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberLoginRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.MemberResponse;
import com.sangkwon.sangkwonplatform.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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
    public ApiResponse<MemberResponse> login(@Valid @RequestBody MemberLoginRequest req,
                                             HttpServletRequest request) {
        MemberResponse me = memberService.login(req);


        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                me.memberId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + me.role().name())));

        SecurityContext context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);

        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return ApiResponse.ok(me);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.<Void>ok(null);
    }
}
