package com.sangkwon.sangkwonplatform.industrytrademark.repository;

import com.sangkwon.sangkwonplatform.industrytrademark.entity.IndustryTrademark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndustryTrademarkRepository extends JpaRepository<IndustryTrademark, Long> {

    // 적재가 업종당 5건만 남기지만, 조회도 상한을 걸어 최신 출원순을 보장한다
    List<IndustryTrademark> findTop5ByIndutyCdOrderByApplDateDescTmIdDesc(String indutyCd);
}
