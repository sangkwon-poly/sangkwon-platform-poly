package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.response.StreetPopResponse;
import com.sangkwon.sangkwonplatform.map.service.StreetPopService;
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

@WebMvcTest(StreetPopController.class)
class StreetPopControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    StreetPopService streetPopService;

    @Test
    void 유동인구_목록을_200으로_반환한다() throws Exception {
        StreetPopResponse s = new StreetPopResponse(
                "20242", "3110001", 1500L, 800L, 700L);
        when(streetPopService.getStreetPops(any())).thenReturn(List.of(s));

        mvc.perform(get("/api/street-pops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trdarCd").value("3110001"))
                .andExpect(jsonPath("$.data[0].totFlpopCo").value(1500));
    }
}
