package com.sangkwon.sangkwonplatform.member.dto.response;

import com.sangkwon.sangkwonplatform.member.entity.Favorite;
import java.time.LocalDateTime;

// NOTE: 상권명(trdarNm)은 TRDAR(map 도메인) 조인이 필요하므로 현재 미포함.
//       map 도메인 연동/조회 정책 확정 후 후속 필드로 추가한다.
public record FavoriteResponse(
        Long favoriteId,
        String trdarCd,
        LocalDateTime createdAt
) {
    public static FavoriteResponse from(Favorite f) {
        return new FavoriteResponse(
                f.getFavoriteId(),
                f.getTrdarCd(),
                f.getCreatedAt()
        );
    }
}
