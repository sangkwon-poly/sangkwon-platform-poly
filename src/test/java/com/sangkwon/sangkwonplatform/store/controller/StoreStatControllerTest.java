package com.sangkwon.sangkwonplatform.store.controller;

import com.sangkwon.sangkwonplatform.store.dto.response.StoreStatResponse;
import com.sangkwon.sangkwonplatform.store.service.StoreStatService;
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

@WebMvcTest(StoreStatController.class)
class StoreStatControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    StoreStatService storeStatService;

    @Test
    void 점포_통계_목록을_200으로_반환한다() throws Exception {
        StoreStatResponse s = new StoreStatResponse(
                "20242", "3110001", "CS100001", 12L, 30L,
                new BigDecimal("3.500"), new BigDecimal("2.100"), 1L, 1L, 4L);
        when(storeStatService.getStoreStats(any())).thenReturn(List.of(s));

        mvc.perform(get("/api/store-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trdarCd").value("3110001"))
                .andExpect(jsonPath("$.data[0].storCo").value(12));
    }
}
