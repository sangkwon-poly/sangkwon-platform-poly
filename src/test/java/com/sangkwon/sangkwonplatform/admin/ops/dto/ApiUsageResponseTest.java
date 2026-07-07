package com.sangkwon.sangkwonplatform.admin.ops.dto;

import com.sangkwon.sangkwonplatform.admin.ops.entity.ApiUsageLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiUsageResponseTest {

    private ApiUsageLog usage(long callCnt, long dailyLimit) {
        ApiUsageLog log = mock(ApiUsageLog.class);
        when(log.getApiName()).thenReturn("SBIZ");
        when(log.getUsageDate()).thenReturn(LocalDate.now());
        when(log.getCallCnt()).thenReturn(callCnt);
        when(log.getDailyLimit()).thenReturn(dailyLimit);
        return log;
    }

    @Test
    void 사용률은_반올림해_퍼센트로_계산한다() {
        assertThat(ApiUsageResponse.from(usage(9100, 10000)).usagePct()).isEqualTo(91);
        assertThat(ApiUsageResponse.from(usage(780, 1000)).usagePct()).isEqualTo(78);
    }

    @Test
    void 한도를_넘어도_100퍼센트로_고정한다() {
        assertThat(ApiUsageResponse.from(usage(12000, 10000)).usagePct()).isEqualTo(100);
    }

    @Test
    void 한도가_0이면_0퍼센트() {
        assertThat(ApiUsageResponse.from(usage(10, 0)).usagePct()).isEqualTo(0);
    }
}
