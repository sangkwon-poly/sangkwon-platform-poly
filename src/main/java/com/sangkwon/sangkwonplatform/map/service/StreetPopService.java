package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.StreetPopSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.StreetPopResponse;
import com.sangkwon.sangkwonplatform.map.repository.StreetPopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreetPopService {

    private final StreetPopRepository streetPopRepository;

    public List<StreetPopResponse> getStreetPops(StreetPopSearchRequest request) {
        return streetPopRepository.search(request.stdrYyquCd(), request.trdarCd()).stream()
                .map(StreetPopResponse::from)
                .toList();
    }
}
