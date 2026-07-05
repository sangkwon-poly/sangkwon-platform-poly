package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.AptSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.AptResponse;
import com.sangkwon.sangkwonplatform.map.repository.AptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AptService {

    private final AptRepository aptRepository;

    public List<AptResponse> getApts(AptSearchRequest request) {
        return aptRepository.search(request.stdrYyquCd(), request.trdarCd()).stream()
                .map(AptResponse::from)
                .toList();
    }
}
