package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.service.ApiUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// R-ONE API 호출마다 REB_RONE 사용량이 집계되고, 집계 실패가 적재를 막지 않는지 검증한다.
@ExtendWith(MockitoExtension.class)
class CommercialRentLoadServiceTest {

    private static final String BASE = "https://www.reb.or.kr/r-one/openapi";

    @Mock JdbcTemplate jt;
    @Mock ApiUsageService apiUsageService;

    private CommercialRentLoadService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        service = new CommercialRentLoadService(jt, apiUsageService);
        ReflectionTestUtils.setField(service, "rebKey", "test-key");
        server = MockRestServiceServer.bindTo(
                (RestTemplate) ReflectionTestUtils.getField(service, "rest")).build();
    }

    @Test
    void RONE_API_호출마다_사용량을_집계한다() {
        when(jt.queryForList(anyString(), eq(String.class))).thenReturn(List.of("20241"));
        // 통계표 목록 1페이지(대상 1건) + 2페이지(빈 응답) + 통계표 데이터 1회 = HTTP 3회
        server.expect(requestTo(BASE + "/SttsApiTbl.do?KEY=test-key&Type=json&pIndex=1&pSize=200"))
                .andRespond(withSuccess(
                        "{\"SttsApiTbl\":[{\"head\":[]},{\"row\":[{\"STATBL_ID\":\"T1\","
                                + "\"STATBL_NM\":\"지역별 임대료((2022년~)_오피스\"}]}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/SttsApiTbl.do?KEY=test-key&Type=json&pIndex=2&pSize=200"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/SttsApiTblData.do?KEY=test-key&Type=json"
                        + "&STATBL_ID=T1&DTACYCLE_CD=QY&pIndex=1&pSize=1000"))
                .andRespond(withSuccess(
                        "{\"SttsApiTblData\":[{\"head\":[]},{\"row\":[{\"WRTTIME_IDTFR_ID\":\"202401\","
                                + "\"DTA_VAL\":\"10.5\",\"CLS_ID\":\"11\",\"CLS_NM\":\"서울\",\"UI_NM\":\"천원\"}]}]}",
                        MediaType.APPLICATION_JSON));

        long loaded = service.load();

        assertThat(loaded).isEqualTo(1);
        verify(apiUsageService, times(3)).record(ExternalApi.REB_RONE);
        server.verify();
    }

    @Test
    void 사용량_집계가_실패해도_적재는_계속된다() {
        when(jt.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        doThrow(new RuntimeException("집계 DB 오류")).when(apiUsageService).record(ExternalApi.REB_RONE);
        server.expect(requestTo(BASE + "/SttsApiTbl.do?KEY=test-key&Type=json&pIndex=1&pSize=200"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        long loaded = service.load();

        assertThat(loaded).isZero();
        server.verify();
    }

    @Test
    void 대상_통계표가_비면_기존_적재분을_지우지_않는다() {
        when(jt.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        // 통계표 목록이 빈/오류 응답(소프트 실패) -> 대상 0건
        server.expect(requestTo(BASE + "/SttsApiTbl.do?KEY=test-key&Type=json&pIndex=1&pSize=200"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.load();

        // COMMERCIAL_RENT를 통삭제하지 않도록 DELETE/INSERT가 나가면 안 된다
        verify(jt, never()).update(anyString());
        verify(jt, never()).batchUpdate(anyString(), anyList());
    }
}
