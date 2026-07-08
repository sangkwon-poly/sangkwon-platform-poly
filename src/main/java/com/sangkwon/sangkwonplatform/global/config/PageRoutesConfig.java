package com.sangkwon.sangkwonplatform.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 정적 페이지를 확장자 없는 경로로 서빙한다 (/admin/batch -> forward /admin/batch.html).
// 링크와 주소창은 깔끔한 경로를 쓰고, 기존 .html 직접 접근도 그대로 동작한다.
// 루트 단축 경로(/login 등)는 forward가 아니라 redirect: member 페이지가 상대경로
// 에셋(css/member.css)을 쓰므로 루트에서 forward하면 스타일 경로가 깨진다.
@Configuration
public class PageRoutesConfig implements WebMvcConfigurer {

    private static final String[] ADMIN_PAGES = {
            "login", "dashboard", "batch", "trdar-admin", "user-admin",
            "member-admin", "notice-admin", "inquiry-admin", "audit-log", "api-usage"
    };
    private static final String[] MAP_PAGES = { "search", "trdar-detail", "compare", "report", "insight" };
    private static final String[] MEMBER_PAGES = { "login", "mypage", "favorites" };

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 섹션 인덱스: 정적 서빙은 하위 디렉터리의 index.html을 자동으로 찾지 않는다
        forward(registry, "/admin", "/admin/index.html");
        forward(registry, "/admin/", "/admin/index.html");
        forward(registry, "/map", "/map/index.html");
        forward(registry, "/map/", "/map/index.html");
        forward(registry, "/support", "/support/support.html");
        forward(registry, "/support/", "/support/support.html");

        for (String page : ADMIN_PAGES) {
            forward(registry, "/admin/" + page, "/admin/" + page + ".html");
        }
        for (String page : MAP_PAGES) {
            forward(registry, "/map/" + page, "/map/" + page + ".html");
        }
        for (String page : MEMBER_PAGES) {
            forward(registry, "/member/" + page, "/member/" + page + ".html");
        }

        // 랜딩 CTA용 단축 경로
        registry.addRedirectViewController("/login", "/member/login");
        registry.addRedirectViewController("/signup", "/member/login?tab=signup");
        registry.addRedirectViewController("/member", "/member/login");
        registry.addRedirectViewController("/member/", "/member/login");
    }

    private void forward(ViewControllerRegistry registry, String path, String resource) {
        registry.addViewController(path).setViewName("forward:" + resource);
    }
}
