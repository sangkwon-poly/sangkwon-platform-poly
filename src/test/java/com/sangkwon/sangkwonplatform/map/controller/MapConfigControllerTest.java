package com.sangkwon.sangkwonplatform.map.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MapConfigController.class)
@TestPropertySource(properties = "kakao.map.js-key=test-kakao-key")
class MapConfigControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 카카오_키를_200으로_반환한다() throws Exception {
        mvc.perform(get("/api/map/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.kakaoJsKey").value("test-kakao-key"));
    }
}
