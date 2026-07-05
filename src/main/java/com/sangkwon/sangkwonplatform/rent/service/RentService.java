package com.sangkwon.sangkwonplatform.rent.service;

import com.sangkwon.sangkwonplatform.rent.dto.request.RentSearchRequest;
import com.sangkwon.sangkwonplatform.rent.dto.response.RentResponse;
import com.sangkwon.sangkwonplatform.rent.repository.CommercialRentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RentService {

    private final CommercialRentRepository commercialRentRepository;

    public List<RentResponse> getRents(RentSearchRequest request) {
        return commercialRentRepository.search(
                        request.regionCd(), request.metricCd(), request.rlstTyCd(), request.stdrYyquCd()).stream()
                .map(RentResponse::from)
                .toList();
    }
}
