package com.sangkwon.sangkwonplatform.member.config;

import com.sangkwon.sangkwonplatform.member.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// 회원 인증 보안 설정 (김민혁 · WBS 4.2). A안(정석): 정적/공개만 열고 개인 API는 JWT 인증.
// ⚠️ 공용 성격 — 우선 member에 임시 배치, 후속 global 이관. admin은 별도 SecurityFilterChain으로 후속 분리.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 정적 화면·리소스
                        .requestMatchers("/", "/error", "/favicon.ico").permitAll()
                        .requestMatchers("/member/**", "/css/**", "/js/**").permitAll()
                        // 공개 API (로그인 전)
                        .requestMatchers(HttpMethod.POST, "/api/members").permitAll()     // 회원가입
                        .requestMatchers("/api/auth/**").permitAll()                      // 로그인/로그아웃
                        .requestMatchers(HttpMethod.POST, "/api/search-logs").permitAll() // 검색 기록(비로그인 허용)
                        // 개인 API (JWT 필요)
                        .requestMatchers("/api/members/me", "/api/favorites/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/search-logs").authenticated()
                        // 나머지(map/admin/support 등)는 임시 개방.
                        // TODO(조장 회신): A안이면 이대로 / B안이면 위 authenticated들도 permitAll로 완화.
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
