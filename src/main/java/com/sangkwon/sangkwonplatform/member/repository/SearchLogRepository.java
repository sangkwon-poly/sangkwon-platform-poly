package com.sangkwon.sangkwonplatform.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sangkwon.sangkwonplatform.member.entity.SearchLog;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    // keyword별 최신 1건만 쓸 거라 넉넉히 받는다.
    List<SearchLog> findTop100ByMemberIdOrderBySearchedAtDesc(Long memberId);

    void deleteByMemberIdAndKeyword(Long memberId, String keyword);

    void deleteByMemberId(Long memberId);
}
