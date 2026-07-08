package com.sangkwon.sangkwonplatform.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// 비밀번호 해시용 인코더(BCrypt). 관리자/회원 인증에서 공용으로 사용.
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // cost 12: 기본값(10)보다 한 단계 높여 관리자 자격증명 해시 강도를 확보한다.
        // 기존 $2a$10 해시도 그대로 검증되고, 재해시되는 시점부터 12가 적용된다.
        return new BCryptPasswordEncoder(12);
    }
}