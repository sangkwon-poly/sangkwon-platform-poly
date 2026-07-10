package com.sangkwon.sangkwonplatform.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

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
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 유료 Gemini 호출을 트리거하는 리포트 생성은 로그인 회원만(비용 남용·예산 소진 방지)
                        .requestMatchers(HttpMethod.POST, "/api/llm-reports/**").authenticated()
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}
