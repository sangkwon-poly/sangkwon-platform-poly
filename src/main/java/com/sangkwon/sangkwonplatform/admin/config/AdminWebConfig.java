package com.sangkwon.sangkwonplatform.admin.config;

import com.sangkwon.sangkwonplatform.admin.adminUser.interceptor.AdminAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 관리자 API 인증 가드 등록. 로그인/로그아웃만 열어두고 나머지 /api/admin/** 는 세션 필수.
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor())
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login", "/api/admin/auth/logout");
    }
}
