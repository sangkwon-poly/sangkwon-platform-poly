package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.response.SalesResponse;
import com.sangkwon.sangkwonplatform.map.service.SalesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SalesController.class)
class SalesControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SalesService salesService;

    @Test
    void 추정매출_목록을_200으로_반환한다() throws Exception {
        SalesResponse s = new SalesResponse(
                "20242", "3110001", "CS100001", 50000000L, 1200L,
                7000000L, 7000000L, 7000000L, 7000000L, 7000000L, 8000000L, 7000000L,
                2000000L, 8000000L, 12000000L, 10000000L, 12000000L, 6000000L,
                26000000L, 24000000L,
                2000000L, 10000000L, 14000000L, 12000000L, 8000000L, 4000000L);
        when(salesService.getSales(any())).thenReturn(List.of(s));

        mvc.perform(get("/api/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trdarCd").value("3110001"))
                .andExpect(jsonPath("$.data[0].thsmonSelngAmt").value(50000000));
    }
}
