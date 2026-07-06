package com.sangkwon.sangkwonplatform.map.controller;

import com.sangkwon.sangkwonplatform.map.dto.response.DistrictGeoResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictResponse;
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
                "3110001", "역삼역", "A", "골목상권", "강남구",
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
        when(districtService.getGeometries(any())).thenReturn(List.of(geo));

        mvc.perform(get("/api/districts/geo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trdarCd").value("3110001"))
                .andExpect(jsonPath("$.data[0].geoJson").exists());
    }
}
