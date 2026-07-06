package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.DistrictSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictGeoResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictResponse;
import com.sangkwon.sangkwonplatform.map.repository.TrdarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DistrictService {

    private final TrdarRepository trdarRepository;

    public List<DistrictResponse> getDistricts(DistrictSearchRequest request) {
        return trdarRepository.search(request.signguCd(), request.trdarSeCd()).stream()
                .map(DistrictResponse::from)
                .toList();
    }

    // 지도 폴리곤용 상권 경계(GeoJSON) 목록
    public List<DistrictGeoResponse> getGeometries(DistrictSearchRequest request) {
        return trdarRepository.searchGeo(request.signguCd(), request.trdarSeCd()).stream()
                .map(DistrictGeoResponse::from)
                .toList();
    }
}
