package com.sangkwon.sangkwonplatform.member.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// 매 요청마다 Authorization: Bearer 토큰을 검증해 SecurityContext에 인증 정보를 심는다.
// principal = memberId(Long) → 컨트롤러에서 @AuthenticationPrincipal Long 로 받는다.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);
            if (jwtProvider.validateToken(token)) {
                Long memberId = jwtProvider.getMemberId(token);
                String role = jwtProvider.getRole(token);
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(memberId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
