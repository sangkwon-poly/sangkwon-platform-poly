package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.response.TrdarChangeResponse;
import com.sangkwon.sangkwonplatform.map.service.TrdarChangeService;
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

@WebMvcTest(TrdarChangeController.class)
class TrdarChangeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TrdarChangeService trdarChangeService;

    @Test
    void 상권변화지표_목록을_200으로_반환한다() throws Exception {
        TrdarChangeResponse t = new TrdarChangeResponse(
                "20242", "3110001", "LL", "다이나믹",
                new BigDecimal("1234.56"), new BigDecimal("789.01"));
        when(trdarChangeService.getTrdarChanges(any())).thenReturn(List.of(t));

        mvc.perform(get("/api/trdar-changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trdarCd").value("3110001"))
                .andExpect(jsonPath("$.data[0].trdarChngeIx").value("LL"));
    }
}
