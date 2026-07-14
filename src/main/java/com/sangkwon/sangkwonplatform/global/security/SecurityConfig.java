package com.sangkwon.sangkwonplatform.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 인증은 대상별로 나뉜다.
// - 관리자(/api/admin/**): Spring Security가 아니라 MVC 인터셉터(AdminIpInterceptor,
//   AdminAuthInterceptor)가 IP 제한·세션 인증을 담당하므로 여기선 통과시킨다.
// - 회원/공개: 이 필터체인에서 인증을 붙인다(현재 개발 중이라 permitAll).
// 비밀번호 인코더는 global.config.PasswordConfig의 공용 빈을 쓴다(여기서 중복 정의하지 않는다).
// spring-boot-starter-security가 클래스패스에 있으므로 이 빈이 반드시 있어야 한다(없으면 관리자·정적까지 기본 인증에 막힘).
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 세션 쿠키 인증이라 CSRF 보호가 필요하다. SPA(정적 JS)가 읽을 수 있게 XSRF-TOKEN 쿠키로 토큰을
                // 내리고, 프런트(common/js/csrf.js)가 상태변경 요청에 X-XSRF-TOKEN 헤더로 되돌려준다.
                // 기본 XOR 핸들러는 매 요청 토큰을 마스킹해 쿠키값과 헤더값이 어긋나므로, 쿠키를 그대로 돌려주는
                // 이 방식에는 평문 핸들러를 쓴다.
                // 관리자 API(/api/admin/**)는 Spring Security가 아니라 IP 허용목록 + 세션 인터셉터로 보호하므로 예외로 둔다.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/admin/**"))
                // 지연 로딩된 토큰을 실제로 한 번 읽어, 첫 GET 응답부터 XSRF-TOKEN 쿠키가 내려가게 한다.
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // 유료 Gemini 호출을 트리거하는 리포트 생성은 로그인 회원만(비용 남용·예산 소진 방지)
                        .requestMatchers(HttpMethod.POST, "/api/llm-reports/**").authenticated()
                        // 업종·상권 동향은 Pro 전용(여기선 로그인만 요구, Pro 판정은 서비스에서 402).
                        // 메서드를 한정하면 HEAD가 매처를 비껴가 익명으로 핸들러에 도달하므로 전 메서드에 건다.
                        .requestMatchers("/api/industry-news-insights/**", "/api/franchise-brand-stats/**",
                                "/api/industry-trademarks/**").authenticated()
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    // CookieCsrfTokenRepository는 토큰이 실제로 사용될 때만 쿠키를 쓴다. 프런트가 첫 화면 로드부터
    // 토큰을 읽을 수 있어야 하므로, 여기서 토큰을 한 번 읽어 응답에 XSRF-TOKEN 쿠키가 실리게 한다.
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
