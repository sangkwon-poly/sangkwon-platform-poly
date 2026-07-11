package com.sangkwon.sangkwonplatform.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;

// 세션 영속화. Spring Boot 4는 세션 저장소별 자동설정(JDBC 등)을 기본 제공하지 않으므로 명시적으로 켠다.
// 세션을 SPRING_SESSION 테이블(db/schema.sql이 관리)에 저장해 재배포/재시작에도 로그인이 유지되고
// 다중 인스턴스에서도 세션을 공유할 수 있다. 만료 세션은 Spring Session이 주기적으로 정리한다.
@Configuration
@EnableJdbcHttpSession
public class SessionConfig {

    // 세션 쿠키 하드닝: 이름 SESSION, HttpOnly(스크립트 접근 차단), SameSite=Lax(크로스사이트 제한).
    // Secure는 HTTPS 배포에서 SESSION_COOKIE_SECURE=true로 강제(base 프로퍼티와 동일한 값을 읽는다).
    @Bean
    public DefaultCookieSerializer cookieSerializer(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        return serializer;
    }
}
