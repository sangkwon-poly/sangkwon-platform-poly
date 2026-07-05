package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.ResidentPopSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.ResidentPopResponse;
import com.sangkwon.sangkwonplatform.map.repository.ResidentPopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResidentPopService {

    private final ResidentPopRepository residentPopRepository;

    public List<ResidentPopResponse> getResidentPops(ResidentPopSearchRequest request) {
        return residentPopRepository.search(request.stdrYyquCd(), request.trdarCd()).stream()
                .map(ResidentPopResponse::from)
                .toList();
    }
}
