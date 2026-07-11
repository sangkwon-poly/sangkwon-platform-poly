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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 공정위 가맹사업 API 호출마다 FTC_FRANCHISE 사용량이 집계되고, 집계 실패가 적재를 막지 않는지 검증한다.
@ExtendWith(MockitoExtension.class)
class FranchiseLoadServiceTest {

    @Mock JdbcTemplate jt;
    @Mock ApiUsageService apiUsageService;

    private FranchiseLoadService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        service = new FranchiseLoadService(jt, apiUsageService);
        ReflectionTestUtils.setField(service, "datagokrKey", "test-key");
        ReflectionTestUtils.setField(service, "ftcKey", "test-key");
        server = MockRestServiceServer.bindTo(
                (RestTemplate) ReflectionTestUtils.getField(service, "rest")).build();
    }

    // 브랜드 건수 확인 1회 + 가맹점수 5개년 5회(JSON) + 정보공개서 5개년 5회(XML) = HTTP 11회
    private void stubEmptyResponses() {
        server.expect(requestTo("https://apis.data.go.kr/1130000/FftcBrandRlsInfo2_Service/getBrandinfo"
                        + "?serviceKey=test-key&pageNo=1&numOfRows=1&resultType=json&jngBizCrtraYr=2023"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        for (int yr = 2019; yr <= 2023; yr++) {
            server.expect(requestTo("https://apis.data.go.kr/1130000/FftcindutyfrcscntstatService/getindutyfrcscntstats"
                            + "?serviceKey=test-key&pageNo=1&numOfRows=1&resultType=json&jngBizCrtraYr=" + yr))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        }
        for (int yr = 2019; yr <= 2023; yr++) {
            server.expect(requestTo("https://franchise.ftc.go.kr/api/search.do?type=list&yr=" + yr
                            + "&serviceKey=test-key"))
                    .andRespond(withSuccess("<response></response>", MediaType.TEXT_XML));
        }
    }

    @Test
    void 공정위_API_호출마다_사용량을_집계한다() {
        stubEmptyResponses();

        long loaded = service.load();

        assertThat(loaded).isZero();
        verify(apiUsageService, times(11)).record(ExternalApi.FTC_FRANCHISE);
        server.verify();
    }

    @Test
    void 사용량_집계가_실패해도_적재는_계속된다() {
        stubEmptyResponses();
        doThrow(new RuntimeException("집계 DB 오류")).when(apiUsageService).record(ExternalApi.FTC_FRANCHISE);

        long loaded = service.load();

        assertThat(loaded).isZero();
        server.verify();
    }
}
