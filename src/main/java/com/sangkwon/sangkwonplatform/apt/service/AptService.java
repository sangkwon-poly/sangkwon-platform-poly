package com.sangkwon.sangkwonplatform.apt.service;

import com.sangkwon.sangkwonplatform.apt.dto.request.AptSearchRequest;
import com.sangkwon.sangkwonplatform.apt.dto.response.AptResponse;
import com.sangkwon.sangkwonplatform.apt.repository.AptRepository;
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
