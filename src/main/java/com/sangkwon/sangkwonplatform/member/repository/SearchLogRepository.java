package com.sangkwon.sangkwonplatform.member.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sangkwon.sangkwonplatform.member.entity.SearchLog;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    // keyword별 최신 1건만 쓸 거라 넉넉히 받는다.
    List<SearchLog> findTop100ByMemberIdOrderBySearchedAtDesc(Long memberId);

    void deleteByMemberIdAndKeyword(Long memberId, String keyword);

    void deleteByMemberId(Long memberId);

    int deleteBySearchedAtBefore(LocalDateTime cutoff);

    // 관리자 개요: 오늘 검색량(서비스 사용 신호)
    long countBySearchedAtGreaterThanEqual(LocalDateTime since);

    // 관리자 대시보드: 기간 내 인기 검색어 집계(수요 신호). 회원이 만든 데이터를 운영 인사이트로 활성화한다.
    @Query("""
            select s.keyword as keyword, count(s) as cnt
            from SearchLog s
            where s.searchedAt >= :since
            group by s.keyword
            order by count(s) desc, s.keyword
            """)
    List<PopularKeyword> findPopularKeywordsSince(@Param("since") LocalDateTime since, Pageable pageable);

    interface PopularKeyword {
        String getKeyword();

        long getCnt();
    }
}
