package com.sangkwon.sangkwonplatform.attraction.controller;

import com.sangkwon.sangkwonplatform.attraction.dto.response.AttractionResponse;
import com.sangkwon.sangkwonplatform.attraction.service.AttractionService;
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

@WebMvcTest(AttractionController.class)
class AttractionControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AttractionService attractionService;

    @Test
    void 집객시설_목록을_200으로_반환한다() throws Exception {
        AttractionResponse a = new AttractionResponse(
                "20242", "3110001", 10L, 2L, 5L, 3L, 1L, 4L);
        when(attractionService.getAttractions(any())).thenReturn(List.of(a));

        mvc.perform(get("/api/attractions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trdarCd").value("3110001"))
                .andExpect(jsonPath("$.data[0].subwayStatnCo").value(2));
    }
}
