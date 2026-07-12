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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    private void stubEmptyBrandAndCountResponses() {
        server.expect(requestTo("https://apis.data.go.kr/1130000/FftcBrandRlsInfo2_Service/getBrandinfo"
                        + "?serviceKey=test-key&pageNo=1&numOfRows=1&resultType=json&jngBizCrtraYr=2023"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        for (int yr = 2019; yr <= 2023; yr++) {
            server.expect(requestTo("https://apis.data.go.kr/1130000/FftcindutyfrcscntstatService/getindutyfrcscntstats"
                            + "?serviceKey=test-key&pageNo=1&numOfRows=1&resultType=json&jngBizCrtraYr=" + yr))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        }
    }

    private void stubDisclosureItems() {
        for (int yr = 2019; yr <= 2023; yr++) {
            server.expect(requestTo("https://franchise.ftc.go.kr/api/search.do?type=list&yr=" + yr
                            + "&serviceKey=test-key"))
                    .andRespond(withSuccess("""
                            <response><item><jngIfrmpSn>%d-1</jngIfrmpSn></item></response>
                            """.formatted(yr), MediaType.TEXT_XML));
        }
    }

    // 브랜드 건수 확인 1회 + 가맹점수 5개년 5회(JSON) + 정보공개서 5개년 5회(XML) = HTTP 11회
    private void stubEmptyResponses() {
        stubEmptyBrandAndCountResponses();
        for (int yr = 2019; yr <= 2023; yr++) {
            server.expect(requestTo("https://franchise.ftc.go.kr/api/search.do?type=list&yr=" + yr
                            + "&serviceKey=test-key"))
                    .andRespond(withSuccess("<response></response>", MediaType.TEXT_XML));
        }
    }

    private void stubResponsesWithDisclosureOnly() {
        stubEmptyBrandAndCountResponses();
        stubDisclosureItems();
    }

    @Test
    void 공정위_API_호출마다_사용량을_집계한다() {
        stubResponsesWithDisclosureOnly();

        long loaded = service.load();

        assertThat(loaded).isEqualTo(5);
        verify(apiUsageService, times(11)).record(ExternalApi.FTC_FRANCHISE);
        server.verify();
    }

    @Test
    void 사용량_집계가_실패해도_적재는_계속된다() {
        stubResponsesWithDisclosureOnly();
        doThrow(new RuntimeException("집계 DB 오류")).when(apiUsageService).record(ExternalApi.FTC_FRANCHISE);

        long loaded = service.load();

        assertThat(loaded).isEqualTo(5);
        server.verify();
    }

    @Test
    void 원천이_비면_기존_적재분을_지우지_않는다() {
        stubEmptyResponses();

        assertThatThrownBy(() -> service.load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FRANCHISE_DISCLOSURE 적재 미완(2019년)");

        verify(jt, never()).update(anyString());
        verify(jt, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    void 페이지_중간_오류로_잘린_스냅샷이면_예외로_롤백하고_DELETE하지_않는다() {
        // 브랜드 total=1000인데 1페이지(500건) 뒤 2페이지가 빈/오류 응답(HTTP 200)이면 절반만 받은 부분 스냅샷이다.
        String base = "https://apis.data.go.kr/1130000/FftcBrandRlsInfo2_Service/getBrandinfo";
        StringBuilder items = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 500; i++) {
            items.append(i == 0 ? "" : ",").append("{\"brandMnno\":\"").append(i).append("\"}");
        }
        items.append("]}");
        server.expect(requestTo(base + "?serviceKey=test-key&pageNo=1&numOfRows=1&resultType=json&jngBizCrtraYr=2023"))
                .andRespond(withSuccess("{\"totalCount\":1000}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(base + "?serviceKey=test-key&pageNo=1&numOfRows=500&resultType=json&jngBizCrtraYr=2023"))
                .andRespond(withSuccess(items.toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(base + "?serviceKey=test-key&pageNo=2&numOfRows=500&resultType=json&jngBizCrtraYr=2023"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FRANCHISE_BRAND 적재 미완");
        // 완결성 검사가 DELETE 앞에서 막으므로 기존 적재분은 보존된다
        verify(jt, never()).update("DELETE FROM FRANCHISE_BRAND");
        server.verify();
    }

    @Test
    void 정보공개서_한_연도라도_XML_파싱에_실패하면_기존_스냅샷을_보존한다() {
        stubEmptyBrandAndCountResponses();
        server.expect(requestTo("https://franchise.ftc.go.kr/api/search.do?type=list&yr=2019&serviceKey=test-key"))
                .andRespond(withSuccess("""
                        <response><item><jngIfrmpSn>2019-1</jngIfrmpSn></item></response>
                        """, MediaType.TEXT_XML));
        server.expect(requestTo("https://franchise.ftc.go.kr/api/search.do?type=list&yr=2020&serviceKey=test-key"))
                .andRespond(withSuccess("<response><item>", MediaType.TEXT_XML));

        assertThatThrownBy(() -> service.load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FRANCHISE_DISCLOSURE 적재 미완(2020년)")
                .hasMessageContaining("XML 파싱 실패");
        verify(jt, never()).update("DELETE FROM FRANCHISE_DISCLOSURE");
        verify(jt, never()).batchUpdate(
                org.mockito.ArgumentMatchers.contains("INSERT INTO FRANCHISE_DISCLOSURE"), anyList());
        server.verify();
    }

    @Test
    void 정보공개서_한_연도가_정상_XML인데_item이_없으면_기존_스냅샷을_보존한다() {
        stubEmptyBrandAndCountResponses();
        server.expect(requestTo("https://franchise.ftc.go.kr/api/search.do?type=list&yr=2019&serviceKey=test-key"))
                .andRespond(withSuccess("""
                        <response><item><jngIfrmpSn>2019-1</jngIfrmpSn></item></response>
                        """, MediaType.TEXT_XML));
        server.expect(requestTo("https://franchise.ftc.go.kr/api/search.do?type=list&yr=2020&serviceKey=test-key"))
                .andRespond(withSuccess("<response></response>", MediaType.TEXT_XML));

        assertThatThrownBy(() -> service.load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FRANCHISE_DISCLOSURE 적재 미완(2020년)")
                .hasMessageContaining("item 0건");
        verify(jt, never()).update("DELETE FROM FRANCHISE_DISCLOSURE");
        server.verify();
    }
}
