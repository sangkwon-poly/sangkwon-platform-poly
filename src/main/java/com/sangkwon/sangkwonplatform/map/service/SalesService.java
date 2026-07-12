package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.SalesSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.SalesListResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.SalesResponse;
import com.sangkwon.sangkwonplatform.map.repository.SalesRepository;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesService {

    // 익명 전체 덤프를 막기 위한 상한. 잘림 여부는 응답 메타데이터로 알린다.
    static final int MAX_ROWS = 5_000;

    private final SalesRepository salesRepository;

    public SalesListResponse getSales(SalesSearchRequest request) {
        requireFilter(request);
        String stdrYyquCd = blankToNull(request.stdrYyquCd());
        String trdarCd = blankToNull(request.trdarCd());
        String indutyCd = blankToNull(request.indutyCd());
        long total = salesRepository.countSearch(stdrYyquCd, trdarCd, indutyCd);
        var items = salesRepository.search(stdrYyquCd, trdarCd, indutyCd, PageRequest.of(0, MAX_ROWS)).stream()
                .map(SalesResponse::from)
                .toList();
        return new SalesListResponse(items, items.size(), MAX_ROWS, total > MAX_ROWS);
    }

    // 필터 없이 전체 행을 내려받는 남용을 막는다. 화면은 항상 상권 코드를 넘긴다.
    private void requireFilter(SalesSearchRequest request) {
        if (isBlank(request.stdrYyquCd()) && isBlank(request.trdarCd()) && isBlank(request.indutyCd())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
