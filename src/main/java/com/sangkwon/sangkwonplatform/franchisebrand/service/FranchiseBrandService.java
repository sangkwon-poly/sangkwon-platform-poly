package com.sangkwon.sangkwonplatform.franchisebrand.service;

import com.sangkwon.sangkwonplatform.franchisebrand.dto.request.FranchiseBrandSearchRequest;
import com.sangkwon.sangkwonplatform.franchisebrand.dto.response.FranchiseBrandResponse;
import com.sangkwon.sangkwonplatform.franchisebrand.repository.FranchiseBrandRepository;
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
