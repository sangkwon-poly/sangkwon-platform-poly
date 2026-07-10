package com.sangkwon.sangkwonplatform.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// 관리자 적재 트리거를 요청 스레드 밖에서 돌리기 위한 전용 풀.
// 배치는 드물게 실행되고 수십 분 걸릴 수 있어 작게 잡되, 동시 실행이 몰려도 요청을 막지 않게 큐로 흡수한다.
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("batchExecutor")
    public Executor batchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("batch-");
        executor.initialize();
        return executor;
    }
}
