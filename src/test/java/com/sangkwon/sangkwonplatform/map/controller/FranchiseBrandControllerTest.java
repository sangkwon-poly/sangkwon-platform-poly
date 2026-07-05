package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.response.FranchiseBrandResponse;
import com.sangkwon.sangkwonplatform.map.service.FranchiseBrandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FranchiseBrandController.class)
class FranchiseBrandControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    FranchiseBrandService franchiseBrandService;

    @Test
    void 프랜차이즈_브랜드_목록을_200으로_반환한다() throws Exception {
        FranchiseBrandResponse megaCoffee = new FranchiseBrandResponse(
                "20240001", "메가커피", "앤하우스", "외식", "커피",
                LocalDate.of(2015, 12, 1));
        when(franchiseBrandService.getFranchiseBrands(any())).thenReturn(List.of(megaCoffee));

        mvc.perform(get("/api/franchise-brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].brandNm").value("메가커피"))
                .andExpect(jsonPath("$.data[0].indutyLclasNm").value("외식"));
    }
}
