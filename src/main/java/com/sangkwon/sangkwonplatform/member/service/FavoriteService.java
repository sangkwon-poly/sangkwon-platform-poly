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

    public List<FavoriteResponse> list(Long memberId) {
        return favoriteRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(FavoriteResponse::from)
                .toList();
    }

    @Transactional
    public FavoriteResponse add(Long memberId, FavoriteCreateRequest req) {
        if (favoriteRepository.existsByMemberIdAndTrdarCd(memberId, req.trdarCd())) {
            throw new BusinessException(ErrorCode.DUPLICATE_FAVORITE);
        }
        Favorite f = Favorite.create(memberId, req.trdarCd());
        favoriteRepository.save(f);
        return FavoriteResponse.from(f);
    }

    @Transactional
    public void remove(Long memberId, String trdarCd) {
        Favorite f = favoriteRepository.findByMemberIdAndTrdarCd(memberId, trdarCd)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_NOT_FOUND));
        favoriteRepository.delete(f);
    }
}
