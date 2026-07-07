package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.dto.request.SearchLogCreateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.SearchLogResponse;
import com.sangkwon.sangkwonplatform.member.entity.SearchLog;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import com.sangkwon.sangkwonplatform.member.repository.SearchLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

// SearchLogService 단위 테스트 — DB/Spring 없이 mock으로 로직만 검증.
@ExtendWith(MockitoExtension.class)
class SearchLogServiceTest {

    @Mock SearchLogRepository searchLogRepository;
    @InjectMocks SearchLogService searchLogService;

    private ErrorCode errorCodeOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    @DisplayName("내 최근 검색: 비인증(null) → M005 (개인 데이터라 인증 필요)")
    void recent_unauthenticated() {
        assertThatThrownBy(() -> searchLogService.recent(null))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    @DisplayName("내 최근 검색: 정상 → 목록 반환")
    void recent_success() {
        when(searchLogRepository.findTop20ByMemberIdOrderBySearchedAtDesc(1L))
                .thenReturn(List.of());

        List<SearchLogResponse> res = searchLogService.recent(1L);

        assertThat(res).isEmpty();
    }

    @Test
    @DisplayName("검색 기록: 로그인 회원 → 저장")
    void log_member() {
        searchLogService.log(1L, new SearchLogCreateRequest("커피", "TR001"));

        verify(searchLogRepository).save(any(SearchLog.class));
    }

    @Test
    @DisplayName("검색 기록: 비로그인(null memberId)도 허용 → 저장 (익명 검색 기록)")
    void log_anonymousAllowed() {
        searchLogService.log(null, new SearchLogCreateRequest("커피", null));

        verify(searchLogRepository).save(any(SearchLog.class));
    }
}
