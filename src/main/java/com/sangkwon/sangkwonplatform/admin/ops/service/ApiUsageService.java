package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.repository.ApiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// 외부 API 일자별 호출량 집계. 어드민 API 사용량 화면이 이 카운터를 읽는다.
@Service
@RequiredArgsConstructor
public class ApiUsageService {

    private final ApiUsageLogRepository apiUsageLogRepository;

    // 유료 호출 슬롯 선점: +1 하고 한도를 넘겼으면 예외로 롤백시켜 증분을 되돌린다.
    // MERGE가 해당 행을 잠가 동시 요청은 여기서 직렬화되므로 한도를 넘어서는 통과가 없다.
    // 선점 후 실제 호출이 실패해도 반납하지 않는다(구글 쿼터도 시도 기준으로 차감된다).
    @Transactional
    public void reserve(ExternalApi api) {
        apiUsageLogRepository.increaseTodayCall(api.name(), api.dailyLimit());
        long count = apiUsageLogRepository.findTodayCallCnt(api.name()).orElse(0L);
        if (count > api.dailyLimit()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "오늘 " + api.label() + " 한도(" + api.dailyLimit() + "회)를 모두 사용했습니다");
        }
    }

    // 집계만 하고 차단하지 않는다. 배치처럼 외부 429를 스스로 처리하는 호출부용.
    // 호출부 트랜잭션과 분리한다(REQUIRES_NEW): 집계 실패가 적재 트랜잭션을 rollback-only로 오염시키지 않고,
    // 적재가 롤백돼도 이미 나간 호출의 집계는 남으며, 적재가 긴 트랜잭션이어도 집계 행 잠금을 바로 푼다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ExternalApi api) {
        apiUsageLogRepository.increaseTodayCall(api.name(), api.dailyLimit());
    }
}
