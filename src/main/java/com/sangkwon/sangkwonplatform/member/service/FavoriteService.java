package com.sangkwon.sangkwonplatform.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sangkwon.sangkwonplatform.member.dto.request.FavoriteCreateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.FavoriteResponse;
import com.sangkwon.sangkwonplatform.member.entity.Favorite;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import com.sangkwon.sangkwonplatform.member.repository.FavoriteRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;

    //favorite 테이블에서 _ member_id로 검색해서 리스트화 해서 건네줌.
    public List<FavoriteResponse> list(Long memberId) {
        requireAuth(memberId);
        return favoriteRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(FavoriteResponse::from)
                .toList();
    }


    @Transactional
    public FavoriteResponse add(Long memberId, FavoriteCreateRequest req) {
        requireAuth(memberId);
        if (favoriteRepository.existsByMemberIdAndTrdarCd(memberId, req.trdarCd())) {
            throw new BusinessException(ErrorCode.DUPLICATE_FAVORITE);
        }
        Favorite f = Favorite.create(memberId, req.trdarCd());
        favoriteRepository.save(f);
        return FavoriteResponse.from(f);
    }

    @Transactional
    public void remove(Long memberId, String trdarCd) {
        requireAuth(memberId);
        Favorite f = favoriteRepository.findByMemberIdAndTrdarCd(memberId, trdarCd)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_NOT_FOUND));
        favoriteRepository.delete(f);
    }

    // 비인증 요청(토큰 없음)이면 memberId가 null → 500 대신 401 (개인 API 보호)
    private void requireAuth(Long memberId) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
