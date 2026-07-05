package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.FranchiseDisclosureSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.FranchiseDisclosureResponse;
import com.sangkwon.sangkwonplatform.map.repository.FranchiseDisclosureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FranchiseDisclosureService {

    private final FranchiseDisclosureRepository franchiseDisclosureRepository;

    public List<FranchiseDisclosureResponse> getFranchiseDisclosures(FranchiseDisclosureSearchRequest request) {
        return franchiseDisclosureRepository.search(
                        request.brandNm(), request.corpNm()).stream()
                .map(FranchiseDisclosureResponse::from)
                .toList();
    }
}
