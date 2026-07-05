package com.sangkwon.sangkwonplatform.store.service;

import com.sangkwon.sangkwonplatform.store.dto.request.StoreStatSearchRequest;
import com.sangkwon.sangkwonplatform.store.dto.response.StoreStatResponse;
import com.sangkwon.sangkwonplatform.store.repository.StoreStatRepository;
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
