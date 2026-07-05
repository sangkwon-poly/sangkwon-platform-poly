package com.sangkwon.sangkwonplatform.rent.controller;

import com.sangkwon.sangkwonplatform.rent.dto.response.RentResponse;
import com.sangkwon.sangkwonplatform.rent.service.RentService;
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

@WebMvcTest(RentController.class)
class RentControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RentService rentService;

    @Test
    void 임대_지표_목록을_200으로_반환한다() throws Exception {
        RentResponse seoulOffice = new RentResponse(
                "500002", "서울", "오피스", "RENT", "임대료", "20242",
                new BigDecimal("28.34"), "천원/㎡");
        when(rentService.getRents(any())).thenReturn(List.of(seoulOffice));

        mvc.perform(get("/api/rents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].metricCd").value("RENT"))
                .andExpect(jsonPath("$.data[0].regionNm").value("서울"));
    }
}
