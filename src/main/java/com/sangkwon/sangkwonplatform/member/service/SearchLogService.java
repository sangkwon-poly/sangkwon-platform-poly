package com.sangkwon.sangkwonplatform.member.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public List<SearchLogResponse> recent(Long memberId, Integer limit) {
        requireAuth(memberId);
        List<SearchLog> rows = searchLogRepository.findTop100ByMemberIdOrderBySearchedAtDesc(memberId);
        // 같은 검색어는 최신 1건만. rows가 최신순이라 먼저 나온 게 최신이다.
        List<SearchLogResponse> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SearchLog s : rows) {
            if (!seen.add(s.getKeyword())) {
                continue;
            }
            result.add(SearchLogResponse.from(s));
            if (limit != null && limit > 0 && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Transactional
    public void delete(Long memberId, String keyword) {
        requireAuth(memberId);
        searchLogRepository.deleteByMemberIdAndKeyword(memberId, keyword);
    }

    @Transactional
    public void deleteAll(Long memberId) {
        requireAuth(memberId);
        searchLogRepository.deleteByMemberId(memberId);
    }

    private void requireAuth(Long memberId) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
