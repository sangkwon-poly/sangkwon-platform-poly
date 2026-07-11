package com.sangkwon.sangkwonplatform.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 주기 작업(@Scheduled) 활성화. 보존 정리 등 운영 자동화를 요청 스레드 밖에서 돌린다.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
