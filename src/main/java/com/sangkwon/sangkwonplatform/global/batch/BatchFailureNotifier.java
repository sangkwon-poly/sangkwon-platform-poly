package com.sangkwon.sangkwonplatform.global.batch;

import com.sangkwon.sangkwonplatform.global.util.ExternalApiMessageSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

// 배치 실패 알림. 적재가 FAILED로 끝나면 설정된 웹훅(Slack 호환 incoming webhook 등)으로 알린다.
// 운영자가 대시보드에 들어와야만 실패를 인지하던 것을 외부 채널로 밀어준다.
// 웹훅 URL 미설정이면 무동작(no-op)이고, 전송 실패는 삼켜서 배치 결과에 영향을 주지 않는다.
@Slf4j
@Component
public class BatchFailureNotifier {

    private final String webhookUrl;
    private final RestClient restClient;

    public BatchFailureNotifier(@Value("${ops.alert.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        // 알림은 부가 작업이라 느린/죽은 웹훅이 배치 스레드를 오래 잡지 않도록 짧은 타임아웃을 건다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public void notifyFailure(String jobName, String datasetCd, String errorMsg) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return; // 미설정: 알림 비활성
        }
        try {
            String text = "[여기콕 배치 실패] " + jobName + " (" + datasetCd + ")\n"
                    + ExternalApiMessageSanitizer.sanitize(errorMsg == null ? "(원인 미상)" : errorMsg);
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("배치 실패 알림 전송 실패(원래 실패는 이미 이력에 기록됨): {}", e.getMessage());
        }
    }
}
