package com.sangkwon.sangkwonplatform.industrynews;

import com.sangkwon.sangkwonplatform.industrynewsInsight.service.IndustryNewsInsightBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

@SpringBootTest
@ActiveProfiles("local")
class IndustryNewsInsightBatchTest {

    @Autowired
    private IndustryNewsInsightBatchService batchService;

    @Test
    void 인사이트_수동_생성_테스트() {
        Map<String, String> indutyNmMap = Map.of(
                "CS100007", "치킨전문점",
                "CS300002", "편의점"
                // 처음엔 이렇게 2~3개만 넣어서 먼저 확인해보세요
                // 잘 되면 INDUTY 마스터 93개 전체로 넓히시면 됩니다
        );

        batchService.generateMonthlyInsights(indutyNmMap);
    }
}
