package com.sangkwon.sangkwonplatform.industrynewsInsight.service;

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

    public Indu
}
