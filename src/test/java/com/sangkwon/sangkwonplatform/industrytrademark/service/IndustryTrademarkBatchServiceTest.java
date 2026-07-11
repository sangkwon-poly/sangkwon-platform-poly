package com.sangkwon.sangkwonplatform.industrytrademark.service;

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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// KIPRIS 상표 적재를 검증한다: 키워드 정제/스킵, 최신 5건 선별, 응답 오류 중단, 집계 실패 격리.
@ExtendWith(MockitoExtension.class)
class IndustryTrademarkBatchServiceTest {

    private static final String BASE =
            "https://plus.kipris.or.kr/kipo-api/kipi/trademarkInfoSearchService/getAdvancedSearch";

    @Mock JdbcTemplate jt;
    @Mock ApiUsageService apiUsageService;

    private IndustryTrademarkBatchService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        service = new IndustryTrademarkBatchService(jt, apiUsageService);
        ReflectionTestUtils.setField(service, "kiprisKey", "test-key");
        server = MockRestServiceServer.bindTo(
                (RestTemplate) ReflectionTestUtils.getField(service, "rest")).build();
    }

    private static String searchUrl(String keyword) {
        return BASE + "?ServiceKey=test-key&asignProduct=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&trademark=true&serviceMark=true&trademarkServiceMark=true&businessEmblem=true"
                + "&collectiveMark=true&geoOrgMark=true&certMark=true&geoCertMark=true&internationalMark=true"
                + "&character=true&figure=true&compositionCharacter=true&figureComposition=true"
                + "&sound=true&color=true&dimension=true&hologram=true&motion=true&visual=true&invisible=true"
                + "&application=true&registration=true&refused=true&expiration=true&withdrawal=true"
                + "&publication=true&cancel=true&abandonment=true"
                + "&sortSpec=AD&descSort=true&pageNo=1&numOfRows=20";
    }

    private static String item(String applNo, String title, String applicant, String date, String status) {
        return "<item>"
                + (applNo == null ? "" : "<applicationNumber>" + applNo + "</applicationNumber>")
                + (title == null ? "" : "<title>" + title + "</title>")
                + "<applicantName>" + applicant + "</applicantName>"
                + "<applicationDate>" + date + "</applicationDate>"
                + "<applicationStatus>" + status + "</applicationStatus>"
                + "</item>";
    }

    private static String okXml(String items) {
        return "<response><header><successYN>Y</successYN><resultCode>00</resultCode></header>"
                + "<body><items>" + items + "</items></body></response>";
    }

    private void stubInduties(Map<String, String> cdToNm) {
        List<Map<String, Object>> rows = cdToNm.entrySet().stream()
                .map(e -> Map.<String, Object>of("INDUTY_CD", e.getKey(), "INDUTY_CD_NM", e.getValue()))
                .toList();
        when(jt.queryForList(anyString())).thenReturn(rows);
    }

    @Test
    void 업종별_최신_상표를_상위_5건까지_적재하고_한_글자_키워드는_건너뛴다() {
        // 정제 결과가 한 글자인 업종은 검색하지 않는다 (실업종은 오버라이드로 잡혀 있어 방어용 가드)
        stubInduties(Map.of("CS100007", "치킨전문점", "CS999999", "면"));
        String items = String.join("",
                item("40-0", "날짜없음상표", "영법인", "", "출원"),
                item("40-1", "가상표", "가법인", "20260619", "출원"),
                item("40-2", "나상표", "나법인", "20260528", "등록"),
                item("40-9", "나상표", "다류출원법인", "20260527", "출원"),
                item(null, "번호없음", "다법인", "20260501", "출원"),
                item("40-3", "라상표", "라법인", "20260422", "거절"),
                item("40-1", "중복출원번호", "마법인", "20260401", "출원"),
                item("40-4", "마상표", "바법인", "20260315", "출원"),
                item("40-5", "바상표", "사법인", "20260302", "출원"),
                item("40-6", "잘림상표", "아법인", "20260201", "출원"));
        server.expect(requestTo(searchUrl("치킨")))
                .andRespond(withSuccess(okXml(items), MediaType.TEXT_XML));

        long loaded = service.load();

        assertThat(loaded).isEqualTo(5);
        verify(jt).update("DELETE FROM INDUSTRY_TRADEMARK");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass(List.class);
        verify(jt).batchUpdate(anyString(), captor.capture());
        List<Object[]> rows = captor.getValue();

        // 출원일 없는 행/번호 없는 행/중복 출원번호/다류 출원의 중복 제목은 빠지고 응답 순서대로 5건까지만 남는다
        assertThat(rows).extracting(r -> (String) r[1])
                .containsExactly("40-1", "40-2", "40-3", "40-4", "40-5");
        assertThat(rows.get(0)[0]).isEqualTo("CS100007");
        assertThat(rows.get(0)[4]).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(rows.get(2)[5]).isEqualTo("거절");
        // HTTP는 치킨 1회뿐(한 글자 업종은 스킵), 호출마다 집계된다
        verify(apiUsageService, times(1)).record(ExternalApi.KIPRIS);
        server.verify();
    }

    @Test
    void 음식점_접미사는_정제되어_검색된다() {
        stubInduties(Map.of("CS100001", "한식음식점"));
        server.expect(requestTo(searchUrl("한식")))
                .andRespond(withSuccess(okXml(item("40-1", "가상표", "가법인", "20260202", "출원")),
                        MediaType.TEXT_XML));

        long loaded = service.load();

        assertThat(loaded).isEqualTo(1);
        server.verify();
    }

    @Test
    void 키_기간_만료_같은_응답_오류면_배치를_중단한다() {
        stubInduties(Map.of("CS100007", "치킨전문점"));
        server.expect(requestTo(searchUrl("치킨")))
                .andRespond(withSuccess("<response><header><successYN>N</successYN>"
                                + "<resultCode>31</resultCode><resultMsg>DEADLINE_HAS_EXPIRED_ERROR</resultMsg>"
                                + "</header></response>",
                        MediaType.TEXT_XML));

        assertThatThrownBy(() -> service.load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEADLINE_HAS_EXPIRED_ERROR");

        verify(jt, never()).update(anyString());
        verify(jt, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    void 키가_없으면_호출_없이_명확한_예외를_던진다() {
        ReflectionTestUtils.setField(service, "kiprisKey", "");

        assertThatThrownBy(() -> service.load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KIPRIS API 키");

        server.verify();
    }

    @Test
    void 결과가_통째로_비면_기존_적재분을_지우지_않는다() {
        stubInduties(Map.of("CS100007", "치킨전문점"));
        server.expect(requestTo(searchUrl("치킨")))
                .andRespond(withSuccess(okXml(""), MediaType.TEXT_XML));

        long loaded = service.load();

        assertThat(loaded).isZero();
        verify(jt, never()).update(anyString());
        verify(jt, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    void 사용량_집계가_실패해도_적재는_계속된다() {
        stubInduties(Map.of("CS100007", "치킨전문점"));
        doThrow(new RuntimeException("집계 DB 오류")).when(apiUsageService).record(ExternalApi.KIPRIS);
        server.expect(requestTo(searchUrl("치킨")))
                .andRespond(withSuccess(okXml(item("40-1", "가상표", "가법인", "20260619", "출원")),
                        MediaType.TEXT_XML));

        long loaded = service.load();

        assertThat(loaded).isEqualTo(1);
        server.verify();
    }
}
