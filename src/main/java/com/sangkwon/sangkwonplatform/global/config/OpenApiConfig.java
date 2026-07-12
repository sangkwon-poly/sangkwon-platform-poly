package com.sangkwon.sangkwonplatform.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// API 문서(springdoc). /swagger-ui/index.html 에서 확인, 스펙은 /v3/api-docs.
// SecurityConfig가 anyRequest().permitAll()이고 관리자 인터셉터는 /api/admin/** 에만 걸리므로 별도 경로 허용은 불필요.
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI yeogikokOpenApi() {
        return new OpenAPI().info(new Info()
                .title("여기콕 API")
                .description("소상공인 상권·업종 분석 + 회원/Pro 결제 + 관리자 백오피스 REST API. 응답은 공통 ApiResponse 봉투로 감싼다.")
                .version("v1"));
    }
}
