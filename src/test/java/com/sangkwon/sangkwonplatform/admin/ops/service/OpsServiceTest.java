package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.NoticeStatus;
import com.sangkwon.sangkwonplatform.admin.notice.repository.NoticeRepository;
import com.sangkwon.sangkwonplatform.admin.ops.dto.ApiUsageResponse;
import com.sangkwon.sangkwonplatform.admin.ops.entity.ApiUsageLog;
import com.sangkwon.sangkwonplatform.admin.ops.repository.AdminAuditLogRepository;
import com.sangkwon.sangkwonplatform.admin.ops.repository.ApiUsageLogRepository;
import com.sangkwon.sangkwonplatform.global.batch.BatchJobLogRepository;
import com.sangkwon.sangkwonplatform.support.repository.SupportProgramRepository;
import com.sangkwon.sangkwonplatform.map.repository.LlmReportRepository;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import com.sangkwon.sangkwonplatform.member.repository.SearchLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// OpsService 단위 테스트. API 사용량 0건 합성과 개요 지표 조합을 검증.
@ExtendWith(MockitoExtension.class)
class OpsServiceTest {

    @Mock BatchJobLogRepository batchJobLogRepository;
    @Mock ApiUsageLogRepository apiUsageLogRepository;
    @Mock AdminAuditLogRepository adminAuditLogRepository;
    @Mock AdminUserRepository adminUserRepository;
    @Mock MemberRepository memberRepository;
    @Mock LlmReportRepository llmReportRepository;
    @Mock PaymentOrderRepository paymentOrderRepository;
    @Mock SearchLogRepository searchLogRepository;
    @Mock NoticeRepository noticeRepository;
    @Mock SupportProgramRepository supportProgramRepository;
    @InjectMocks OpsService opsService;

    @Test
    void 인기_검색어를_기간_컷오프로_집계한다() {
        SearchLogRepository.PopularKeyword k = mock(SearchLogRepository.PopularKeyword.class);
        when(k.getKeyword()).thenReturn("강남역");
        when(k.getCnt()).thenReturn(12L);
        when(searchLogRepository.findPopularKeywordsSince(any(), any())).thenReturn(List.of(k));

        var res = opsService.popularSearches(7, 10);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).keyword()).isEqualTo("강남역");
        assertThat(res.get(0).count()).isEqualTo(12L);
    }

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

    @Test
    void 개요는_매출과_유효_구독자까지_함께_담는다() {
        when(memberRepository.count()).thenReturn(10L);
        when(memberRepository.countByCreatedAtGreaterThanEqual(any())).thenReturn(2L);
        when(llmReportRepository.count()).thenReturn(5L);
        when(llmReportRepository.countByCreatedAtGreaterThanEqual(any())).thenReturn(1L);
        when(paymentOrderRepository.sumAmountByStatusSince(eq(PaymentStatus.PAID), any())).thenReturn(240_000L);
        when(memberRepository.countByPlanUntilAfter(any())).thenReturn(3L);
        when(searchLogRepository.countBySearchedAtGreaterThanEqual(any())).thenReturn(7L);
        when(noticeRepository.countByStatus(NoticeStatus.PUBLISHED)).thenReturn(4L);
        when(supportProgramRepository.count()).thenReturn(105L);

        var res = opsService.overview();

        assertThat(res.memberCount()).isEqualTo(10L);
        assertThat(res.monthRevenue()).isEqualTo(240_000L);
        assertThat(res.activeProCount()).isEqualTo(3L);
        // 개요 확장 지표: 오늘 검색·게시 공지·지원사업 수
        assertThat(res.todaySearchCount()).isEqualTo(7L);
        assertThat(res.publishedNoticeCount()).isEqualTo(4L);
        assertThat(res.supportProgramCount()).isEqualTo(105L);
    }

    @Test
    void 인기검색어_limit이_0이하여도_클램프해_예외없이_조회한다() {
        when(searchLogRepository.findPopularKeywordsSince(any(), any())).thenReturn(List.of());

        // limit=0이면 PageRequest.of(0,0)이 IllegalArgumentException(500)을 던진다. 클램프로 예외 없이 진행돼야 한다.
        opsService.popularSearches(7, 0);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(searchLogRepository).findPopularKeywordsSince(any(), cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(1);
    }
}
