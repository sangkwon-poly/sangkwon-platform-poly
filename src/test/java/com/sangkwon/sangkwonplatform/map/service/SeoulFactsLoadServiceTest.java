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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 서울 오픈API 호출마다 SEOUL 사용량이 집계되고, 집계 실패가 적재를 막지 않는지 검증한다.
@ExtendWith(MockitoExtension.class)
class SeoulFactsLoadServiceTest {

    @Mock JdbcTemplate jt;
    @Mock ApiUsageService apiUsageService;

    private SeoulFactsLoadService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        service = new SeoulFactsLoadService(jt, apiUsageService);
        ReflectionTestUtils.setField(service, "seoulKey", "test-key");
        server = MockRestServiceServer.bindTo(
                (RestTemplate) ReflectionTestUtils.getField(service, "rest")).build();
    }

    // 전체 건수 확인 1회 + 페이지 조회 1회 = HTTP 2회가 나가는 최소 시나리오
    private void stubTrdarChange() {
        List<Map<String, Object>> columns = List.of(Map.of("COLUMN_NAME", "TRDAR_CD", "DATA_TYPE", "VARCHAR2"));
        when(jt.queryForList(anyString(), eq("TRDAR_CHANGE"))).thenReturn(columns);
        server.expect(requestTo("http://openapi.seoul.go.kr:8088/test-key/json/VwsmTrdarIxQq/1/1/"))
                .andRespond(withSuccess(
                        "{\"VwsmTrdarIxQq\":{\"list_total_count\":1,\"row\":[{\"TRDAR_CD\":\"3110001\"}]}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://openapi.seoul.go.kr:8088/test-key/json/VwsmTrdarIxQq/1/1000/"))
                .andRespond(withSuccess(
                        "{\"VwsmTrdarIxQq\":{\"row\":[{\"TRDAR_CD\":\"3110001\"}]}}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void 서울_API_호출마다_사용량을_집계한다() {
        stubTrdarChange();

        long loaded = service.loadTrdarChange();

        assertThat(loaded).isEqualTo(1);
        verify(apiUsageService, times(2)).record(ExternalApi.SEOUL);
        server.verify();
    }

    @Test
    void 사용량_집계가_실패해도_적재는_계속된다() {
        stubTrdarChange();
        doThrow(new RuntimeException("집계 DB 오류")).when(apiUsageService).record(ExternalApi.SEOUL);

        long loaded = service.loadTrdarChange();

        assertThat(loaded).isEqualTo(1);
        server.verify();
    }

    // total(3)만큼 예고됐지만 페이지 도중 빈 블록(HTTP 200에 결과 없음)이 오면 잘린 스냅샷을 커밋하지 않고
    // 예외로 롤백시킨다. 예외를 던지면 클래스 @Transactional이 DELETE까지 되돌려 기존 데이터가 보존된다.
    @Test
    void 수신이_예고_건수보다_적으면_예외로_롤백한다() {
        List<Map<String, Object>> columns = List.of(Map.of("COLUMN_NAME", "TRDAR_CD", "DATA_TYPE", "VARCHAR2"));
        when(jt.queryForList(anyString(), eq("TRDAR_CHANGE"))).thenReturn(columns);
        // 전체 건수 확인: total=3 (샘플 1행 포함)
        server.expect(requestTo("http://openapi.seoul.go.kr:8088/test-key/json/VwsmTrdarIxQq/1/1/"))
                .andRespond(withSuccess(
                        "{\"VwsmTrdarIxQq\":{\"list_total_count\":3,\"row\":[{\"TRDAR_CD\":\"3110001\"}]}}",
                        MediaType.APPLICATION_JSON));
        // 실제 페이지 조회는 결과 없음(에러성 200) → 0행 수신 < 3행 예고
        server.expect(requestTo("http://openapi.seoul.go.kr:8088/test-key/json/VwsmTrdarIxQq/1/1000/"))
                .andRespond(withSuccess(
                        "{\"VwsmTrdarIxQq\":{\"row\":[]}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.loadTrdarChange())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("적재 미완");
        server.verify();
    }
}
