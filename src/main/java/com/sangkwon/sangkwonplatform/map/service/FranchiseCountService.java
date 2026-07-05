package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.FranchiseCountSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.FranchiseCountResponse;
import com.sangkwon.sangkwonplatform.map.repository.FranchiseCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FranchiseCountService {

    private final FranchiseCountRepository franchiseCountRepository;

    public List<FranchiseCountResponse> getFranchiseCounts(FranchiseCountSearchRequest request) {
        return franchiseCountRepository.search(
                        request.baseYear(), request.areaCd(), request.indutyNm()).stream()
                .map(FranchiseCountResponse::from)
                .toList();
    }
}
