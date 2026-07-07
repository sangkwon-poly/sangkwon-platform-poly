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
        requireAuth(memberId);
        return searchLogRepository.findTop20ByMemberIdOrderBySearchedAtDesc(memberId).stream()
                .map(SearchLogResponse::from)
                .toList();
    }

    private void requireAuth(Long memberId) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
