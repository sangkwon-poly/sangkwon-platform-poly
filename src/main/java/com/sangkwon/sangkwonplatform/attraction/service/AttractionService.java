package com.sangkwon.sangkwonplatform.attraction.service;

import com.sangkwon.sangkwonplatform.attraction.dto.request.AttractionSearchRequest;
import com.sangkwon.sangkwonplatform.attraction.dto.response.AttractionResponse;
import com.sangkwon.sangkwonplatform.attraction.repository.AttractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttractionService {

    private final AttractionRepository attractionRepository;

    public List<AttractionResponse> getAttractions(AttractionSearchRequest request) {
        return attractionRepository.search(request.stdrYyquCd(), request.trdarCd()).stream()
                .map(AttractionResponse::from)
                .toList();
    }
}
