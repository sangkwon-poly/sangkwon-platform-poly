package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.TrdarChangeSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.TrdarChangeResponse;
import com.sangkwon.sangkwonplatform.map.repository.TrdarChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrdarChangeService {

    private final TrdarChangeRepository trdarChangeRepository;

    public List<TrdarChangeResponse> getTrdarChanges(TrdarChangeSearchRequest request) {
        return trdarChangeRepository.search(request.stdrYyquCd(), request.trdarCd()).stream()
                .map(TrdarChangeResponse::from)
                .toList();
    }
}
