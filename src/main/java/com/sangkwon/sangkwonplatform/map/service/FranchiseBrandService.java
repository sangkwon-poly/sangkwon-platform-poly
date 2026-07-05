package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.FranchiseBrandSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.FranchiseBrandResponse;
import com.sangkwon.sangkwonplatform.map.repository.FranchiseBrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FranchiseBrandService {

    private final FranchiseBrandRepository franchiseBrandRepository;

    public List<FranchiseBrandResponse> getFranchiseBrands(FranchiseBrandSearchRequest request) {
        return franchiseBrandRepository.search(
                        request.brandNm(), request.indutyLclasNm()).stream()
                .map(FranchiseBrandResponse::from)
                .toList();
    }
}
