package com.sangkwon.sangkwonplatform.global.batch;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 배치 실패 알림: 웹훅 설정 시 Slack 호환 payload로 POST하고, 미설정이면 아무 것도 하지 않는다.
class BatchFailureNotifierTest {

    private static final String WEBHOOK = "https://hooks.example.com/T000/B000/xxx";

    @Test
    void 웹훅이_설정되면_실패_내용을_POST한다() {
        BatchFailureNotifier notifier = new BatchFailureNotifier(WEBHOOK);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(notifier, "restClient", builder.build());
        server.expect(requestTo(WEBHOOK))
                .andExpect(method(POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("업종 상표 동향")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("KIPRIS 키 만료")))
                .andRespond(withSuccess());

        notifier.notifyFailure("업종 상표 동향", "INDUSTRY_TRADEMARK", "KIPRIS 키 만료");

        server.verify();
    }

    @Test
    void 웹훅_미설정이면_아무_것도_하지_않는다() {
        BatchFailureNotifier notifier = new BatchFailureNotifier("");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(notifier, "restClient", builder.build());

        notifier.notifyFailure("업종 상표 동향", "INDUSTRY_TRADEMARK", "오류");

        server.verify(); // 요청이 없어야 통과
    }
}
