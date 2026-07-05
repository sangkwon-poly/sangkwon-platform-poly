package com.sangkwon.sangkwonplatform.apt.controller;

import com.sangkwon.sangkwonplatform.apt.dto.response.AptResponse;
import com.sangkwon.sangkwonplatform.apt.service.AptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AptController.class)
class AptControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AptService aptService;

    @Test
    void 아파트_목록을_200으로_반환한다() throws Exception {
        AptResponse a = new AptResponse(
                "20242", "3110001", 5L, 1200L, new BigDecimal("84.50"), 950000000L);
        when(aptService.getApts(any())).thenReturn(List.of(a));

        mvc.perform(get("/api/apts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trdarCd").value("3110001"))
                .andExpect(jsonPath("$.data[0].aptHshldCo").value(1200));
    }
}
