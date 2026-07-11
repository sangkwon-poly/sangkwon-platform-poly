package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.service.ApiUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 공정위 브랜드별 가맹점 현황 적재를 검증한다: 업종 매핑 필터, 가맹점수 상위 5개 선별, 집계 실패 격리.
@ExtendWith(MockitoExtension.class)
class FranchiseBrandStatLoadServiceTest {

    private static final String BASE =
            "https://apis.data.go.kr/1130000/FftcBrandFrcsStatsService/getBrandFrcsStats";

    @Mock JdbcTemplate jt;
    @Mock ApiUsageService apiUsageService;

    private FranchiseBrandStatLoadService service;
    private MockRestServiceServer server;
    private final int thisYear = LocalDate.now().getYear();

    @BeforeEach
    void setUp() {
        service = new FranchiseBrandStatLoadService(jt, apiUsageService);
        ReflectionTestUtils.setField(service, "datagokrKey", "test-key");
        server = MockRestServiceServer.bindTo(
                (RestTemplate) ReflectionTestUtils.getField(service, "rest")).build();
    }

    private String probeUrl(int year) {
        return BASE + "?serviceKey=test-key&pageNo=1&numOfRows=1&resultType=json&yr=" + year;
    }

    private String pageUrl(int year, int page) {
        return BASE + "?serviceKey=test-key&pageNo=" + page + "&numOfRows=500&resultType=json&yr=" + year;
    }

    private static String item(String mlsfc, String brand, String corp, String frcsCnt, String avgSales) {
        return "{\"indutyLclasNm\":\"외식\",\"indutyMlsfcNm\":\"" + mlsfc + "\",\"brandNm\":\"" + brand
                + "\",\"corpNm\":\"" + corp + "\""
                + (frcsCnt == null ? "" : ",\"frcsCnt\":\"" + frcsCnt + "\"")
                + (avgSales == null ? "" : ",\"avrgSlsAmt\":\"" + avgSales + "\"")
                + "}";
    }

    // 치킨 6곳(1곳은 후행 공백 분류) + 미매핑 피자 1곳 + 가맹점수 없는 1곳 + 중복 브랜드 1곳
    private void stubOnePageOfBrands(int year) {
        server.expect(requestTo(probeUrl(year)))
                .andRespond(withSuccess("{\"resultCode\":\"00\",\"totalCount\":9,\"items\":[]}",
                        MediaType.APPLICATION_JSON));
        String items = String.join(",",
                item("치킨", "가브랜드", "가법인", "100", "250000"),
                item("치킨", "나브랜드", "나법인", "500", "620000"),
                item("치킨", "다브랜드", "다법인", "300", null),
                item("치킨", "라브랜드", "라법인", "200", "410000"),
                item("치킨", "마브랜드", "마법인", "50", "180000"),
                item("치킨 ", "바브랜드", "바법인", "400", "530000"),
                item("피자", "피자브랜드", "피자법인", "900", "700000"),
                item("치킨", "무점포브랜드", "무점포법인", null, "300000"),
                item("치킨", "나브랜드", "중복법인", "9999", "1"));
        server.expect(requestTo(pageUrl(year, 1)))
                .andRespond(withSuccess("{\"resultCode\":\"00\",\"totalCount\":9,\"items\":[" + items + "]}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void 매핑된_업종만_가맹점수_상위_5개를_적재한다() {
        stubOnePageOfBrands(thisYear);

        long loaded = service.load();

        assertThat(loaded).isEqualTo(5);
        verify(jt).update("DELETE FROM FRANCHISE_BRAND_STAT");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass(List.class);
        verify(jt).batchUpdate(anyString(), captor.capture());
        List<Object[]> rows = captor.getValue();

        // 미매핑(피자)/가맹점수 없음/중복 브랜드는 빠지고, 가맹점수 내림차순 5개만 남는다
        assertThat(rows).extracting(r -> (String) r[2])
                .containsExactly("나브랜드", "바브랜드", "다브랜드", "라브랜드", "가브랜드");
        assertThat(rows).allSatisfy(r -> {
            assertThat(r[0]).isEqualTo("CS100007");
            assertThat(r[1]).isEqualTo(thisYear);
        });
        // 후행 공백 분류도 trim 후 매핑되고, 원본 업종명/평균매출이 함께 저장된다
        assertThat(rows.get(1)[4]).isEqualTo("치킨");
        assertThat(rows.get(0)[6]).isEqualTo(620000L);
        assertThat(rows.get(2)[6]).isNull();
        // 재시도 없이 성공했으므로 HTTP 호출(연도 탐색 1 + 페이지 1)마다 한 번씩 집계된다
        verify(apiUsageService, times(2)).record(ExternalApi.FTC_FRANCHISE);
    }

    @Test
    void 원천_응답이_비면_기존_적재분을_지우지_않는다() {
        for (int yr = thisYear; yr >= 2022; yr--) {
            server.expect(requestTo(probeUrl(yr)))
                    .andRespond(withSuccess("{\"resultCode\":\"00\",\"totalCount\":0,\"items\":[]}",
                            MediaType.APPLICATION_JSON));
        }

        long loaded = service.load();

        assertThat(loaded).isZero();
        verify(jt, never()).update(anyString());
        verify(jt, never()).batchUpdate(anyString(), anyList());
        server.verify();
    }

    @Test
    void 건수는_있는데_매핑되는_브랜드가_없으면_기존_적재분을_지우지_않는다() {
        server.expect(requestTo(probeUrl(thisYear)))
                .andRespond(withSuccess("{\"resultCode\":\"00\",\"totalCount\":1,\"items\":[]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(pageUrl(thisYear, 1)))
                .andRespond(withSuccess("{\"resultCode\":\"00\",\"totalCount\":1,\"items\":["
                                + item("피자", "피자브랜드", "피자법인", "900", "700000") + "]}",
                        MediaType.APPLICATION_JSON));

        long loaded = service.load();

        assertThat(loaded).isZero();
        verify(jt, never()).update(anyString());
        verify(jt, never()).batchUpdate(anyString(), anyList());
        server.verify();
    }

    @Test
    void 사용량_집계가_실패해도_적재는_계속된다() {
        stubOnePageOfBrands(thisYear);
        doThrow(new RuntimeException("집계 DB 오류")).when(apiUsageService).record(ExternalApi.FTC_FRANCHISE);

        long loaded = service.load();

        assertThat(loaded).isEqualTo(5);
        server.verify();
    }
}
