package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.global.batch.BatchJobLog;
import com.sangkwon.sangkwonplatform.global.batch.BatchJobLogRepository;
import com.sangkwon.sangkwonplatform.global.batch.BatchJobSpec;
import com.sangkwon.sangkwonplatform.global.batch.BatchStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 스테일 RUNNING 초기화: 해당 데이터셋의 RUNNING을 FAILED로 내리고 감사 로그를 남긴다.
@ExtendWith(MockitoExtension.class)
class BatchAdminServiceTest {

    @Mock BatchJobLogRepository batchJobLogRepository;
    @Mock BatchAsyncRunner batchAsyncRunner;
    @Mock AdminAuditService adminAuditService;
    @Mock DatasetStatsReader datasetStatsReader;
    @InjectMocks BatchAdminService batchAdminService;

    @Test
    void 초기화하면_해당_데이터셋의_RUNNING을_FAILED로_내리고_감사를_남긴다() {
        AdminSession admin = new AdminSession(1L, "admin", "최고관리자", AdminRole.SUPER_ADMIN, 0);
        BatchJobLog log = BatchJobLog.start(
                new BatchJobSpec("업종 상표 동향 적재", "INDUSTRY_TRADEMARK", null, "admin"));
        when(batchJobLogRepository.findByDatasetCdAndStatus("INDUSTRY_TRADEMARK", BatchStatus.RUNNING))
                .thenReturn(List.of(log));

        int count = batchAdminService.reset("INDUSTRY_TRADEMARK", admin, null);

        assertThat(count).isEqualTo(1);
        assertThat(log.getStatus()).isEqualTo(BatchStatus.FAILED);
        verify(batchJobLogRepository).saveAll(List.of(log));
        verify(adminAuditService).record(eq(1L), eq(AuditAction.BATCH_RESET),
                eq("BATCH_JOB"), eq("INDUSTRY_TRADEMARK"), anyString(), isNull());
    }
}
