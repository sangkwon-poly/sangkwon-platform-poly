package com.sangkwon.sangkwonplatform.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sangkwon.sangkwonplatform.member.dto.request.SearchLogCreateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.SearchLogResponse;
import com.sangkwon.sangkwonplatform.member.entity.SearchLog;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import com.sangkwon.sangkwonplatform.member.repository.SearchLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SearchLogService {

    private final SearchLogRepository searchLogRepository;

    @Transactional
    public void log(Long memberId, SearchLogCreateRequest req) {
        searchLogRepository.save(SearchLog.create(memberId, req.keyword(), req.trdarCd()));
    }

    public List<SearchLogResponse> recent(Long memberId) {
        requireAuth(memberId);   // '내 최근 검색'은 개인 데이터 → 비인증이면 401 (500 아님)
        return searchLogRepository.findTop20ByMemberIdOrderBySearchedAtDesc(memberId).stream()
                .map(SearchLogResponse::from)
                .toList();
    }

    // 검색 기록(log)은 비로그인도 허용(memberId null OK). '내 최근 검색'(recent)만 개인 데이터라 인증 필요.
    private void requireAuth(Long memberId) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
