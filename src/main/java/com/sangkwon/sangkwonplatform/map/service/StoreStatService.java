package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.StoreStatSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.StoreStatResponse;
import com.sangkwon.sangkwonplatform.map.repository.StoreStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreStatService {

    private final StoreStatRepository storeStatRepository;

    public List<StoreStatResponse> getStoreStats(StoreStatSearchRequest request) {
        return storeStatRepository.search(request.stdrYyquCd(), request.trdarCd(), request.indutyCd()).stream()
                .map(StoreStatResponse::from)
                .toList();
    }
}
