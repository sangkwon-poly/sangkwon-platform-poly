package com.sangkwon.sangkwonplatform.member.repository;

import com.sangkwon.sangkwonplatform.member.entity.Favorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {


    List<Favorite> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    boolean existsByMemberIdAndTrdarCd(Long memberId, String trdarCd);

    Optional<Favorite> findByMemberIdAndTrdarCd(Long memberId, String trdarCd);
}
