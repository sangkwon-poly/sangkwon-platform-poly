package com.sangkwon.sangkwonplatform.admin.config;

import com.sangkwon.sangkwonplatform.admin.account.interceptor.AdminAuthInterceptor;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdminArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

// 관리자 API 인증 인프라: /api/admin/** 세션 가드 + @LoginAdmin 주입
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor())
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login", "/api/admin/auth/logout");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginAdminArgumentResolver());
    }
}
