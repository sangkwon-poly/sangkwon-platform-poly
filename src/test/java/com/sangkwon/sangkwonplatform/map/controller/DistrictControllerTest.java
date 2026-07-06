package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.response.DistrictGeoResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictSummaryResponse;
import com.sangkwon.sangkwonplatform.map.service.DistrictService;
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

@WebMvcTest(DistrictController.class)
class DistrictControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DistrictService districtService;

    @Test
    void 상권_목록을_200으로_반환한다() throws Exception {
        DistrictResponse gangnam = new DistrictResponse(
                "3110001", "역삼역", "A", "골목상권", "11680", "강남구",
                new BigDecimal("127.03"), new BigDecimal("37.50"));
        when(districtService.getDistricts(any())).thenReturn(List.of(gangnam));

        mvc.perform(get("/api/districts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trdarCd").value("3110001"))
                .andExpect(jsonPath("$.data[0].signguNm").value("강남구"));
    }

    @Test
    void 상권_경계를_200으로_반환한다() throws Exception {
        DistrictGeoResponse geo = new DistrictGeoResponse(
                "3110001", "역삼역", "{\"type\":\"Polygon\",\"coordinates\":[]}");
        when(districtService.getGeometries(any(), any())).thenReturn(List.of(geo));

        mvc.perform(get("/api/districts/geo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trdarCd").value("3110001"))
                .andExpect(jsonPath("$.data[0].geoJson").exists());
    }

    @Test
    void 단일_상권을_200으로_반환한다() throws Exception {
        DistrictResponse d = new DistrictResponse(
                "3110001", "역삼역", "A", "골목상권", "11680", "강남구",
                new BigDecimal("127.03"), new BigDecimal("37.50"));
        when(districtService.getDistrict("3110001")).thenReturn(d);

        mvc.perform(get("/api/districts/3110001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trdarNm").value("역삼역"));
    }

    @Test
    void 상권_요약_검색을_200으로_반환한다() throws Exception {
        DistrictSummaryResponse s = new DistrictSummaryResponse(
                "3110001", "역삼역", "강남구",
                new BigDecimal("127.03"), new BigDecimal("37.50"),
                74_000_000_000L, 590_000L, 3596L, "LL", "다이나믹", "20261");
        when(districtService.getSummaries(null, null, "강남", null)).thenReturn(List.of(s));

        mvc.perform(get("/api/districts/summary").param("keyword", "강남"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].trdarNm").value("역삼역"))
                .andExpect(jsonPath("$.data[0].salesAmt").value(74000000000L));
    }
}
