package com.sangkwon.sangkwonplatform.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

// 스케줄러 클러스터 단일 실행. 다중 인스턴스에서 같은 @Scheduled가 동시에 발화하면 결제 대사가 토스를
// N배 호출하는 등 낭비/충돌이 생기므로, 각 작업을 SHEDLOCK 테이블 락으로 클러스터 전체에서 1회만 실행한다.
// 새 인프라 없이 기존 Oracle DB를 재사용한다. defaultLockAtMostFor는 락 보유 상한(작업이 죽어도 이 시간 뒤 자동 해제).
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime() // 락 만료 판정을 앱 서버 시계가 아닌 DB 시계로 해 인스턴스 간 시계 차이를 배제
                        .build());
    }
}
