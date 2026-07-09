package com.sangkwon.sangkwonplatform.industrynews;

import com.sangkwon.sangkwonplatform.industrynewsInsight.service.IndustryNewsInsightBatchService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 실제 네이버/Gemini API를 전 업종에 대해 호출하는 적재용 배치라 일반 빌드에서는 돌리지 않는다.
// 월별 인사이트를 실제로 채워야 할 때만 이 애노테이션을 잠시 풀고 로컬에서 수동 실행한다.
@Disabled("전 업종 라이브 API 적재 배치. 수동 실행 전용")
@SpringBootTest
@ActiveProfiles("local")
class IndustryNewsInsightBatchTest {

    @Autowired
    private IndustryNewsInsightBatchService service;

    @Test
    void 전체_업종_인사이트_적재_테스트() {
        service.generateAllIndustryInsights();
    }
}
