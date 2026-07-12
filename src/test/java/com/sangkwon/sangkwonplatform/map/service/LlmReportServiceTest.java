package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.service.ApiUsageService;
import com.sangkwon.sangkwonplatform.map.entity.LlmReport;
import com.sangkwon.sangkwonplatform.map.entity.Trdar;
import com.sangkwon.sangkwonplatform.map.repository.LlmReportRepository;
import com.sangkwon.sangkwonplatform.map.repository.SalesRepository;
import com.sangkwon.sangkwonplatform.map.repository.StoreStatRepository;
import com.sangkwon.sangkwonplatform.map.repository.StreetPopRepository;
import com.sangkwon.sangkwonplatform.map.repository.TrdarRepository;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// HTTP 호출 이전의 방어 분기(키 없음/상권 없음/업종 없음/무료 한도/일일 한도)를 검증한다. 이 경로들은 Gemini(RestClient)를 건드리지 않는다.
class LlmReportServiceTest {

    private final TrdarRepository trdarRepository = mock(TrdarRepository.class);
    private final SalesRepository salesRepository = mock(SalesRepository.class);
    private final StoreStatRepository storeStatRepository = mock(StoreStatRepository.class);
    private final StreetPopRepository streetPopRepository = mock(StreetPopRepository.class);
    private final LlmReportRepository llmReportRepository = mock(LlmReportRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final ApiUsageService apiUsageService = mock(ApiUsageService.class);
    private final AiReportQuota aiReportQuota = mock(AiReportQuota.class);
    private final RestClient restClient = mock(RestClient.class);

    private LlmReportService service(String apiKey) {
        return new LlmReportService(trdarRepository, salesRepository, storeStatRepository,
                streetPopRepository, llmReportRepository, memberRepository, apiUsageService, aiReportQuota, restClient,
                apiKey, "gemini-2.5-flash");
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    private static Member freeMember() {
        return Member.create("user", "hash", "user@test.com", "무료회원");
    }

    private static Member proMember() {
        Member m = Member.create("pro", "hash", "pro@test.com", "프로회원");
        m.activatePro(LocalDateTime.now().plusDays(30));
        return m;
    }

    @Test
    void API_키가_비어있으면_503을_던지고_AI를_호출하지_않는다() {
        assertThatThrownBy(() -> service("").generate(1L, "3110001", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(503));
        verifyNoInteractions(restClient);
    }

    @Test
    void 상권을_찾지_못하면_404를_던진다() {
        when(trdarRepository.findById("9999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("test-key").generate(1L, "9999999", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        verifyNoInteractions(restClient);
    }

    @Test
    void 업종을_찾지_못하면_404를_던진다() {
        when(trdarRepository.findById("3110001")).thenReturn(Optional.of(mock(Trdar.class)));
        when(llmReportRepository.findIndutyName("CS999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("test-key").generate(1L, "3110001", "CS999999"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        verifyNoInteractions(restClient);
    }

    @Test
    void 무료_회원이_이번_달_한도를_채우면_402를_던지고_슬롯을_쓰지_않는다() {
        when(trdarRepository.findById("3110001")).thenReturn(Optional.of(mock(Trdar.class)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(freeMember()));
        // 무료 회원 월 슬롯 원자 선점이 한도 초과로 402를 던진다(카운트-저장 사이 우회 없음)
        doThrow(new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "월 한도"))
                .when(aiReportQuota).reserve(eq(1L), eq(false), anyInt(), anyInt());

        assertThatThrownBy(() -> service("test-key").generate(1L, "3110001", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(402));
        verify(apiUsageService, never()).reserve(any());
        verifyNoInteractions(restClient);
    }

    @Test
    void Pro_회원은_월_한도를_넘겨도_막지_않고_유료_경로로_넘어간다() {
        when(trdarRepository.findById("3110001")).thenReturn(Optional.of(mock(Trdar.class)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(proMember()));
        // Pro는 월 한도 미적용이라 회원 슬롯 선점(no-op)을 통과하고 전역 GEMINI 예약까지 도달한다(여기서 429).
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "한도 초과"))
                .when(apiUsageService).reserve(ExternalApi.GEMINI);

        // 402(월 한도)가 아니라 429(reserve까지 도달)여야 월 한도 우회가 확인된다
        assertThatThrownBy(() -> service("test-key").generate(1L, "3110001", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(429));
    }

    @Test
    void 회원별_시간당_상한을_넘기면_429를_던지고_슬롯을_쓰지_않는다() {
        when(trdarRepository.findById("3110001")).thenReturn(Optional.of(mock(Trdar.class)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(freeMember()));
        // 시간당 상한 초과는 무료·Pro 공통으로 슬롯 선점에서 429로 막고, 전역 예약·AI 호출로 가지 않는다.
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "시간당 초과"))
                .when(aiReportQuota).reserve(eq(1L), eq(false), anyInt(), anyInt());

        assertThatThrownBy(() -> service("test-key").generate(1L, "3110001", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(429));
        verify(apiUsageService, never()).reserve(any());
        verifyNoInteractions(restClient);
    }

    @Test
    void previousQuarter_직전_분기_코드를_만든다() {
        assertThat(LlmReportService.previousQuarter("20244")).isEqualTo("20243");
        assertThat(LlmReportService.previousQuarter("20242")).isEqualTo("20241");
        // 1분기면 전년도 4분기
        assertThat(LlmReportService.previousQuarter("20241")).isEqualTo("20234");
    }

    @Test
    void 오늘_생성된_리포트가_있으면_재사용하고_Gemini를_호출하지_않는다() {
        when(trdarRepository.findById("3110001")).thenReturn(Optional.of(mock(Trdar.class)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(freeMember()));
        LlmReport recent = mock(LlmReport.class);
        when(recent.getCreatedAt()).thenReturn(LocalDateTime.now()); // 오늘 생성 = 신선
        when(llmReportRepository.findLatest(eq("3110001"), isNull(), any())).thenReturn(List.of(recent));
        when(llmReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service("test-key").generate(1L, "3110001", null);

        // 캐시 재사용이면 전역 예산 예약도 Gemini 호출도 없이 이 회원 이력만 저장된다
        verify(apiUsageService, never()).reserve(any());
        verifyNoInteractions(restClient);
        verify(llmReportRepository).save(any());
    }

    @Test
    void 일일_GEMINI_한도를_초과하면_429를_던지고_AI를_호출하지_않는다() {
        when(trdarRepository.findById("3110001")).thenReturn(Optional.of(mock(Trdar.class)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(freeMember()));
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "한도 초과"))
                .when(apiUsageService).reserve(ExternalApi.GEMINI);

        assertThatThrownBy(() -> service("test-key").generate(1L, "3110001", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(429));
        verifyNoInteractions(restClient);
    }
}
