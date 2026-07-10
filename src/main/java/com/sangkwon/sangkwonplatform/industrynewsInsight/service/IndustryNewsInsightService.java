package com.sangkwon.sangkwonplatform.industrynewsInsight.service;

import com.sangkwon.sangkwonplatform.industrynewsInsight.dto.response.IndustryNewsInsightResponse;
import com.sangkwon.sangkwonplatform.industrynewsInsight.repository.IndustryNewsInsightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IndustryNewsInsightService {

    private final IndustryNewsInsightRepository repository;

    public IndustryNewsInsightService(IndustryNewsInsightRepository repository){
        this.repository = repository;
    }

    public IndustryNewsInsightResponse getLatestInsight(String indutyCd) {

        return repository.findFirstByIndutyCdOrderByYearMonthDesc(indutyCd)
                .map(entity -> IndustryNewsInsightResponse.from(entity))
                .orElse(new IndustryNewsInsightResponse(
                        indutyCd,
                        null,
                        null,
                        "아직 생성된 인사이트가 없습니다.",
                        0
                ));
    }
}
