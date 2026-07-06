package com.sangkwon.sangkwonplatform.map.service;

import com.sangkwon.sangkwonplatform.map.dto.request.DistrictSearchRequest;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictGeoResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictResponse;
import com.sangkwon.sangkwonplatform.map.dto.response.DistrictSummaryResponse;
import com.sangkwon.sangkwonplatform.map.repository.TrdarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    // 지도 폴리곤용 상권 경계(GeoJSON) 목록. trdarCd를 주면 해당 상권 하나만.
    public List<DistrictGeoResponse> getGeometries(DistrictSearchRequest request, String trdarCd) {
        return trdarRepository.searchGeo(request.signguCd(), request.trdarSeCd(), trdarCd).stream()
                .map(DistrictGeoResponse::from)
                .toList();
    }

    // 단일 상권 조회 (상세 화면용)
    public DistrictResponse getDistrict(String trdarCd) {
        return trdarRepository.findById(trdarCd)
                .map(DistrictResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상권을 찾을 수 없습니다"));
    }

    // 상권 요약 목록 (지도·검색용). quarter가 없으면 최신 분기, indutyCd는 매출·점포에만 적용.
    public List<DistrictSummaryResponse> getSummaries(String signguCd, String trdarSeCd, String keyword,
                                                      String quarter, String indutyCd) {
        return trdarRepository.searchSummary(signguCd, trdarSeCd, keyword, quarter, indutyCd).stream()
                .map(DistrictSummaryResponse::from)
                .toList();
    }

    // 조회 가능한 분기 목록
    public List<String> getQuarters() {
        return trdarRepository.findSalesQuarters();
    }
}
