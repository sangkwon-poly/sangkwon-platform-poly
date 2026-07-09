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
    private IndustryNewsInsightBatchService service;

    @Test
    void 전체_업종_인사이트_적재_테스트() {
        service.generateAllIndustryInsights();
    }
}
