package com.sangkwon.sangkwonplatform.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sangkwon.sangkwonplatform.member.entity.SearchLog;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    List<SearchLog> findTop20ByMemberIdOrderBySearchedAtDesc(Long memberId);
}
