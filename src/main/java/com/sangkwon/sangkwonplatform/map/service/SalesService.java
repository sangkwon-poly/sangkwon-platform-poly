package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.SalesSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.SalesResponse;
import com.sangkwon.sangkwonplatform.map.repository.SalesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesService {

    private final SalesRepository salesRepository;

    public List<SalesResponse> getSales(SalesSearchRequest request) {
        return salesRepository.search(
                        request.stdrYyquCd(), request.trdarCd(), request.indutyCd()).stream()
                .map(SalesResponse::from)
                .toList();
    }
}
