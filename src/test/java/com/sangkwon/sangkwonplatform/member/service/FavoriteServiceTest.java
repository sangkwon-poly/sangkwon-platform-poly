package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.dto.request.FavoriteCreateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.FavoriteResponse;
import com.sangkwon.sangkwonplatform.member.entity.Favorite;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import com.sangkwon.sangkwonplatform.member.repository.FavoriteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

// FavoriteService 단위 테스트 — DB/Spring 없이 mock으로 로직만 검증.
@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock FavoriteRepository favoriteRepository;
    @InjectMocks FavoriteService favoriteService;

    private ErrorCode errorCodeOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    @DisplayName("목록: 비인증(null) → M005")
    void list_unauthenticated() {
        assertThatThrownBy(() -> favoriteService.list(null))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    @DisplayName("목록: 정상 → 회원의 찜 목록 반환")
    void list_success() {
        when(favoriteRepository.findByMemberIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(Favorite.create(1L, "TR001"), Favorite.create(1L, "TR002")));

        List<FavoriteResponse> res = favoriteService.list(1L);

        assertThat(res).extracting(FavoriteResponse::trdarCd).containsExactly("TR001", "TR002");
    }

    @Test
    @DisplayName("추가: 비인증(null) → M005")
    void add_unauthenticated() {
        assertThatThrownBy(() -> favoriteService.add(null, new FavoriteCreateRequest("TR001")))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    @DisplayName("추가: 이미 찜한 상권 → M006 (중복 거부)")
    void add_duplicate() {
        when(favoriteRepository.existsByMemberIdAndTrdarCd(1L, "TR001")).thenReturn(true);

        assertThatThrownBy(() -> favoriteService.add(1L, new FavoriteCreateRequest("TR001")))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.DUPLICATE_FAVORITE));
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    @DisplayName("추가: 정상 → 저장하고 응답 반환")
    void add_success() {
        when(favoriteRepository.existsByMemberIdAndTrdarCd(1L, "TR001")).thenReturn(false);

        FavoriteResponse res = favoriteService.add(1L, new FavoriteCreateRequest("TR001"));

        assertThat(res.trdarCd()).isEqualTo("TR001");
        verify(favoriteRepository).save(any(Favorite.class));
    }

    @Test
    @DisplayName("추가: 동시 요청으로 UNIQUE 위반(save 예외) → M006으로 변환")
    void add_concurrentUniqueViolation() {
        when(favoriteRepository.existsByMemberIdAndTrdarCd(1L, "TR001")).thenReturn(false);
        when(favoriteRepository.save(any(Favorite.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> favoriteService.add(1L, new FavoriteCreateRequest("TR001")))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.DUPLICATE_FAVORITE));
    }

    @Test
    @DisplayName("삭제: 없는 찜 → M008")
    void remove_notFound() {
        when(favoriteRepository.findByMemberIdAndTrdarCd(1L, "TR404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.remove(1L, "TR404"))
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.FAVORITE_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제: 정상 → delete 호출")
    void remove_success() {
        Favorite f = Favorite.create(1L, "TR001");
        when(favoriteRepository.findByMemberIdAndTrdarCd(1L, "TR001")).thenReturn(Optional.of(f));

        favoriteService.remove(1L, "TR001");

        verify(favoriteRepository).delete(f);
    }
}
