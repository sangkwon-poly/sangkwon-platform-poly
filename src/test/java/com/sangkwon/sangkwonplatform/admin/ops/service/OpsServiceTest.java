package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.ops.dto.ApiUsageResponse;
import com.sangkwon.sangkwonplatform.admin.ops.entity.ApiUsageLog;
import com.sangkwon.sangkwonplatform.admin.ops.repository.AdminAuditLogRepository;
import com.sangkwon.sangkwonplatform.admin.ops.repository.ApiUsageLogRepository;
import com.sangkwon.sangkwonplatform.global.batch.BatchJobLogRepository;
import com.sangkwon.sangkwonplatform.map.repository.LlmReportRepository;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// OpsService 단위 테스트. API 사용량 응답이 집계 행과 계측 대상 0건 행을 함께 담는지만 검증.
@ExtendWith(MockitoExtension.class)
class OpsServiceTest {

    @Mock BatchJobLogRepository batchJobLogRepository;
    @Mock ApiUsageLogRepository apiUsageLogRepository;
    @Mock AdminAuditLogRepository adminAuditLogRepository;
    @Mock AdminUserRepository adminUserRepository;
    @Mock MemberRepository memberRepository;
    @Mock LlmReportRepository llmReportRepository;
    @InjectMocks OpsService opsService;

    private ApiUsageLog usage(String apiName, long callCnt, long dailyLimit) {
        ApiUsageLog log = mock(ApiUsageLog.class);
        when(log.getApiName()).thenReturn(apiName);
        when(log.getUsageDate()).thenReturn(LocalDate.now());
        when(log.getCallCnt()).thenReturn(callCnt);
        when(log.getDailyLimit()).thenReturn(dailyLimit);
        return log;
    }

    @Test
    void 집계가_없는_계측_대상도_0건으로_함께_노출한다() {
        when(apiUsageLogRepository.findByUsageDateOrderByApiName(any())).thenReturn(List.of());

        List<ApiUsageResponse> res = opsService.todayApiUsage();

        assertThat(res).extracting(ApiUsageResponse::apiName)
                .contains("GEMINI", "GEMINI_NEWS", "SEOUL", "REB_RONE", "FTC_FRANCHISE");
        assertThat(res).allSatisfy(a -> assertThat(a.callCnt()).isZero());
    }

    @Test
    void 집계_행이_있으면_0건_합성보다_우선한다() {
        // when() 안에서 usage()를 만들면 스터빙이 중첩되므로 목을 먼저 완성한다
        ApiUsageLog row = usage("GEMINI", 3, 1000);
        when(apiUsageLogRepository.findByUsageDateOrderByApiName(any())).thenReturn(List.of(row));

        List<ApiUsageResponse> res = opsService.todayApiUsage();

        ApiUsageResponse gemini = res.stream().filter(a -> a.apiName().equals("GEMINI")).findFirst().orElseThrow();
        assertThat(gemini.callCnt()).isEqualTo(3);
        ApiUsageResponse news = res.stream().filter(a -> a.apiName().equals("GEMINI_NEWS")).findFirst().orElseThrow();
        assertThat(news.callCnt()).isZero();
        assertThat(news.dailyLimit()).isEqualTo(1000);
    }
}
