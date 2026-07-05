package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.response.ResidentPopResponse;
import com.sangkwon.sangkwonplatform.map.service.ResidentPopService;
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

@WebMvcTest(ResidentPopController.class)
class ResidentPopControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ResidentPopService residentPopService;

    @Test
    void 상주인구_목록을_200으로_반환한다() throws Exception {
        ResidentPopResponse r = new ResidentPopResponse(
                "20242", "3110001", 1500L, 720L, 780L, 600L);
        when(residentPopService.getResidentPops(any())).thenReturn(List.of(r));

        mvc.perform(get("/api/resident-pops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trdarCd").value("3110001"))
                .andExpect(jsonPath("$.data[0].totRepopCo").value(1500));
    }
}
