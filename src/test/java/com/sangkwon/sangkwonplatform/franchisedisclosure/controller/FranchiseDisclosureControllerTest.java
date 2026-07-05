package com.sangkwon.sangkwonplatform.franchisedisclosure.controller;

import com.sangkwon.sangkwonplatform.franchisedisclosure.dto.response.FranchiseDisclosureResponse;
import com.sangkwon.sangkwonplatform.franchisedisclosure.service.FranchiseDisclosureService;
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

@WebMvcTest(FranchiseDisclosureController.class)
class FranchiseDisclosureControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    FranchiseDisclosureService franchiseDisclosureService;

    @Test
    void 정보공개서_목록을_200으로_반환한다() throws Exception {
        FranchiseDisclosureResponse mega = new FranchiseDisclosureResponse(
                "2024-001", "주식회사 앤하우스", "메가커피", "123-45-67890",
                "https://franchise.ftc.go.kr/viewer/2024-001");
        when(franchiseDisclosureService.getFranchiseDisclosures(any())).thenReturn(List.of(mega));

        mvc.perform(get("/api/franchise-disclosures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].brandNm").value("메가커피"))
                .andExpect(jsonPath("$.data[0].corpNm").value("주식회사 앤하우스"));
    }
}
