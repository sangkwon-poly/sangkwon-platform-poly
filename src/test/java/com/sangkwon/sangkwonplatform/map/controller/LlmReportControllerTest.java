package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.response.LlmReportResponse;
import com.sangkwon.sangkwonplatform.map.service.LlmReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LlmReportController.class)
class LlmReportControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LlmReportService llmReportService;

    @Test
    void 리포트를_생성해_200으로_반환한다() throws Exception {
        LlmReportResponse r = new LlmReportResponse(
                "3110001", "20261", "분석 본문", "gemini-2.5-flash", LocalDateTime.now());
        when(llmReportService.generate("3110001")).thenReturn(r);

        mvc.perform(post("/api/llm-reports/3110001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultText").value("분석 본문"))
                .andExpect(jsonPath("$.data.modelName").value("gemini-2.5-flash"));
    }

    @Test
    void 최근_리포트를_조회한다() throws Exception {
        LlmReportResponse r = new LlmReportResponse(
                "3110001", "20261", "분석 본문", "gemini-2.5-flash", LocalDateTime.now());
        when(llmReportService.latest("3110001")).thenReturn(r);

        mvc.perform(get("/api/llm-reports/3110001/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trdarCd").value("3110001"));
    }
}
