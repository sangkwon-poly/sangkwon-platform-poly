package com.sangkwon.sangkwonplatform.global.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

// 적재 로더 공용 RestTemplate 팩토리. 외부 오픈API가 응답 없이 매달릴 때 배치 스레드가
// 무한 블록되지 않도록 연결/읽기 타임아웃을 건다(소켓 hang -> 유한 시간 실패로 전환).
public final class LoaderHttp {

    private LoaderHttp() {
    }

    public static RestTemplate timed() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return new RestTemplate(factory);
    }
}
