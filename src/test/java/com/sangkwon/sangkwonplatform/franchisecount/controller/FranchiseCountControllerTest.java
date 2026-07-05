package com.sangkwon.sangkwonplatform.franchisecount.controller;

import com.sangkwon.sangkwonplatform.franchisecount.dto.response.FranchiseCountResponse;
import com.sangkwon.sangkwonplatform.franchisecount.service.FranchiseCountService;
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

@WebMvcTest(FranchiseCountController.class)
class FranchiseCountControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    FranchiseCountService franchiseCountService;

    @Test
    void 가맹점수_목록을_200으로_반환한다() throws Exception {
        FranchiseCountResponse seoulCvs = new FranchiseCountResponse(
                1L, 2024, "11110", "서울종로구", "편의점", 120L, new BigDecimal("12.3400"));
        when(franchiseCountService.getFranchiseCounts(any())).thenReturn(List.of(seoulCvs));

        mvc.perform(get("/api/franchise-counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].areaCd").value("11110"))
                .andExpect(jsonPath("$.data[0].indutyNm").value("편의점"));
    }
}
