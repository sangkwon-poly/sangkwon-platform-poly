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

// SearchLogService 단위 테스트. DB/Spring 없이 mock으로 로직만 검증.
@ExtendWith(MockitoExtension.class)
class SearchLogServiceTest {

    @Mock SearchLogRepository searchLogRepository;
    @Mock SearchLogRateLimiter searchLogRateLimiter;
    @InjectMocks SearchLogService searchLogService;

    private ErrorCode errorCodeOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    @DisplayName("내 최근 검색: 비인증(null) → M005 (개인 데이터라 인증 필요)")
    void recent_unauthenticated() {
        assertThatThrownBy(() -> searchLogService.recent(null, null))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    @DisplayName("내 최근 검색: 정상 → 목록 반환")
    void recent_success() {
        when(searchLogRepository.findTop100ByMemberIdOrderBySearchedAtDesc(1L))
                .thenReturn(List.of());

        List<SearchLogResponse> res = searchLogService.recent(1L, null);

        assertThat(res).isEmpty();
    }

    @Test
    @DisplayName("내 최근 검색: 같은 검색어는 최신 1건만(중복 제거), 최신순 유지")
    void recent_dedup() {
        when(searchLogRepository.findTop100ByMemberIdOrderBySearchedAtDesc(1L))
                .thenReturn(List.of(
                        SearchLog.create(1L, "커피", null),
                        SearchLog.create(1L, "커피", null),
                        SearchLog.create(1L, "지하철", null)));

        List<SearchLogResponse> res = searchLogService.recent(1L, null);

        assertThat(res).extracting(SearchLogResponse::keyword).containsExactly("커피", "지하철");
    }

    @Test
    @DisplayName("내 최근 검색: limit로 개수 제한")
    void recent_limit() {
        when(searchLogRepository.findTop100ByMemberIdOrderBySearchedAtDesc(1L))
                .thenReturn(List.of(
                        SearchLog.create(1L, "A", null),
                        SearchLog.create(1L, "B", null),
                        SearchLog.create(1L, "C", null)));

        List<SearchLogResponse> res = searchLogService.recent(1L, 2);

        assertThat(res).extracting(SearchLogResponse::keyword).containsExactly("A", "B");
    }

    @Test
    @DisplayName("검색 기록: 로그인 회원 → 저장")
    void log_member() {
        when(searchLogRateLimiter.tryAcquire(1L, "127.0.0.1")).thenReturn(true);

        searchLogService.log(1L, new SearchLogCreateRequest("커피", "TR001"), "127.0.0.1");

        verify(searchLogRepository).save(any(SearchLog.class));
    }

    @Test
    @DisplayName("검색 기록: 비로그인(null memberId)도 허용 → 저장 (익명 검색 기록)")
    void log_anonymousAllowed() {
        when(searchLogRateLimiter.tryAcquire(null, "127.0.0.1")).thenReturn(true);

        searchLogService.log(null, new SearchLogCreateRequest("커피", null), "127.0.0.1");

        verify(searchLogRepository).save(any(SearchLog.class));
    }

    @Test
    @DisplayName("검색 기록: 레이트리밋 초과 → M020")
    void log_rateLimited() {
        when(searchLogRateLimiter.tryAcquire(null, "127.0.0.1")).thenReturn(false);

        assertThatThrownBy(() -> searchLogService.log(null, new SearchLogCreateRequest("커피", null), "127.0.0.1"))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.SEARCH_LOG_RATE_LIMITED));
    }

    @Test
    @DisplayName("검색어 삭제: 비인증(null) → M005")
    void delete_unauthenticated() {
        assertThatThrownBy(() -> searchLogService.delete(null, "커피"))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    @DisplayName("검색어 삭제: 정상 → keyword 기준 삭제")
    void delete_success() {
        searchLogService.delete(1L, "커피");

        verify(searchLogRepository).deleteByMemberIdAndKeyword(1L, "커피");
    }

    @Test
    @DisplayName("전체 삭제: 비인증(null) → M005")
    void deleteAll_unauthenticated() {
        assertThatThrownBy(() -> searchLogService.deleteAll(null))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    @DisplayName("전체 삭제: 정상 → 회원 전체 삭제")
    void deleteAll_success() {
        searchLogService.deleteAll(1L);

        verify(searchLogRepository).deleteByMemberId(1L);
    }
}
