package com.sangkwon.sangkwonplatform.member.dto.response;

import com.sangkwon.sangkwonplatform.member.entity.Favorite;
import java.time.LocalDateTime;

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
