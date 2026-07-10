package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.global.batch.BatchJobExecutor;
import com.sangkwon.sangkwonplatform.global.batch.BatchJobSpec;
import com.sangkwon.sangkwonplatform.global.batch.Dataset;
import com.sangkwon.sangkwonplatform.industrynewsInsight.service.IndustryNewsInsightBatchService;
import com.sangkwon.sangkwonplatform.map.service.CommercialRentLoadService;
import com.sangkwon.sangkwonplatform.map.service.FranchiseLoadService;
import com.sangkwon.sangkwonplatform.map.service.SeoulFactsLoadService;
import com.sangkwon.sangkwonplatform.support.service.SupportProgramLoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// 적재를 요청 스레드 밖(batchExecutor)에서 실행한다. 배치는 수십 분 걸릴 수 있어 HTTP 응답을 붙잡으면 안 된다.
// BatchJobExecutor가 BATCH_JOB_LOG에 RUNNING -> SUCCESS/FAILED 이력을 남긴다.
@Component
@RequiredArgsConstructor
public class BatchAsyncRunner {

    private final BatchJobExecutor batchJobExecutor;
    private final IndustryNewsInsightBatchService industryNewsInsightBatchService;
    private final SupportProgramLoadService supportProgramLoadService;
    private final FranchiseLoadService franchiseLoadService;
    private final CommercialRentLoadService commercialRentLoadService;
    private final SeoulFactsLoadService seoulFactsLoadService;

    @Async("batchExecutor")
    public void run(Dataset dataset, String triggeredBy, Runnable onComplete) {
        try {
            batchJobExecutor.run(
                    new BatchJobSpec(dataset.jobName(), dataset.code(), null, triggeredBy),
                    () -> loader(dataset));
        } finally {
            // 실행이 끝나면(성공/실패 무관) 중복실행 예약을 반드시 푼다
            onComplete.run();
        }
    }

    // APP 티어 데이터셋만 여기 도달한다(BatchAdminService에서 검증). 그 외는 방어적으로 막는다.
    private long loader(Dataset dataset) {
        return switch (dataset) {
            case INDUSTRY_NEWS -> industryNewsInsightBatchService.generateAllIndustryInsights();
            case SUPPORT_PROGRAM -> supportProgramLoadService.load();
            case FRANCHISE -> franchiseLoadService.load();
            case RENT -> commercialRentLoadService.load();
            case SALES -> seoulFactsLoadService.loadSales();
            case STORE_STAT -> seoulFactsLoadService.loadStoreStat();
            case TRDAR_CHANGE -> seoulFactsLoadService.loadTrdarChange();
            case STREET_POP -> seoulFactsLoadService.loadStreetPop();
            case RESIDENT_POP -> seoulFactsLoadService.loadResidentPop();
            case ATTRACTION -> seoulFactsLoadService.loadAttraction();
            case APT -> seoulFactsLoadService.loadApt();
            default -> throw new IllegalStateException("앱에서 실행할 수 없는 데이터셋: " + dataset.code());
        };
    }
}
