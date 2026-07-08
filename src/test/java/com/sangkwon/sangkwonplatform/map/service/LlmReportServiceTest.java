package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.entity.Trdar;
import com.sangkwon.sangkwonplatform.map.repository.LlmReportRepository;
import com.sangkwon.sangkwonplatform.map.repository.SalesRepository;
import com.sangkwon.sangkwonplatform.map.repository.StoreStatRepository;
import com.sangkwon.sangkwonplatform.map.repository.StreetPopRepository;
import com.sangkwon.sangkwonplatform.map.repository.TrdarRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// HTTP 호출 이전의 방어 분기(키 없음/상권 없음/업종 없음)를 검증한다. 이 경로들은 Gemini(RestClient)를 건드리지 않는다.
class LlmReportServiceTest {

    private final TrdarRepository trdarRepository = mock(TrdarRepository.class);
    private final SalesRepository salesRepository = mock(SalesRepository.class);
    private final StoreStatRepository storeStatRepository = mock(StoreStatRepository.class);
    private final StreetPopRepository streetPopRepository = mock(StreetPopRepository.class);
    private final LlmReportRepository llmReportRepository = mock(LlmReportRepository.class);
    private final RestClient restClient = mock(RestClient.class);

    private LlmReportService service(String apiKey) {
        return new LlmReportService(trdarRepository, salesRepository, storeStatRepository,
                streetPopRepository, llmReportRepository, restClient, apiKey, "gemini-2.5-flash");
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void API_키가_비어있으면_503을_던지고_AI를_호출하지_않는다() {
        assertThatThrownBy(() -> service("").generate("3110001", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(503));
        verifyNoInteractions(restClient);
    }

    @Test
    void 상권을_찾지_못하면_404를_던진다() {
        when(trdarRepository.findById("9999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("test-key").generate("9999999", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        verifyNoInteractions(restClient);
    }

    @Test
    void 업종을_찾지_못하면_404를_던진다() {
        when(trdarRepository.findById("3110001")).thenReturn(Optional.of(mock(Trdar.class)));
        when(llmReportRepository.findIndutyName("CS999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("test-key").generate("3110001", "CS999999"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        verifyNoInteractions(restClient);
    }
}
