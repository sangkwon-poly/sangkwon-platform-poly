package com.sangkwon.sangkwonplatform.admin.config;

import com.sangkwon.sangkwonplatform.admin.account.interceptor.AdminAuthInterceptor;
import com.sangkwon.sangkwonplatform.admin.account.interceptor.AdminIpInterceptor;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdminArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

// 관리자 API 인증 인프라: IP 제한 → 세션 가드 → @LoginAdmin 주입
@Configuration
@RequiredArgsConstructor
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminIpInterceptor adminIpInterceptor;
    // ObjectProvider로 받아 요청 시점에만 리포지토리를 꺼낸다(웹 슬라이스 테스트에서 JPA 빈이 없어도 설정 로드 가능)
    private final ObjectProvider<AdminUserRepository> adminUserRepositoryProvider;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // IP 제한은 로그인 포함 모든 관리자 API에 먼저 적용
        registry.addInterceptor(adminIpInterceptor)
                .addPathPatterns("/api/admin/**");
        // 세션 인증은 로그인/로그아웃만 열어둔다
        registry.addInterceptor(new AdminAuthInterceptor(adminUserRepositoryProvider::getObject))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login", "/api/admin/auth/logout");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginAdminArgumentResolver());
    }
}
