package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.ops.dto.ApiUsageResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.AuditLogResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.BatchLogResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.OverviewResponse;
import com.sangkwon.sangkwonplatform.admin.ops.entity.AdminAuditLog;
import com.sangkwon.sangkwonplatform.admin.ops.repository.AdminAuditLogRepository;
import com.sangkwon.sangkwonplatform.admin.ops.repository.ApiUsageLogRepository;
import com.sangkwon.sangkwonplatform.global.batch.BatchJobLogRepository;
import com.sangkwon.sangkwonplatform.map.repository.LlmReportRepository;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpsService {

    private final BatchJobLogRepository batchJobLogRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AdminUserRepository adminUserRepository;
    private final MemberRepository memberRepository;
    private final LlmReportRepository llmReportRepository;

    // 회원·리포트 누적 수와 오늘 증가분. 개요 상단 지표 카드용
    public OverviewResponse overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return new OverviewResponse(
                memberRepository.count(),
                memberRepository.countByCreatedAtGreaterThanEqual(todayStart),
                llmReportRepository.count(),
                llmReportRepository.countByCreatedAtGreaterThanEqual(todayStart));
    }

    public List<BatchLogResponse> recentBatches(int limit) {
        return batchJobLogRepository.findByOrderByStartedAtDesc(Limit.of(limit))
                .stream().map(BatchLogResponse::from).toList();
    }

    public List<ApiUsageResponse> todayApiUsage() {
        return apiUsageLogRepository.findByUsageDateOrderByApiName(LocalDate.now())
                .stream().map(ApiUsageResponse::from).toList();
    }

    public List<AuditLogResponse> recentAudits(int limit) {
        List<AdminAuditLog> logs = adminAuditLogRepository.findByOrderByCreatedAtDesc(Limit.of(limit));
        Map<Long, String> loginById = adminUserRepository.findAllById(
                        logs.stream().map(AdminAuditLog::getAdminId).distinct().toList())
                .stream().collect(Collectors.toMap(AdminUser::getAdminId,
                        u -> u.getLoginId() == null ? "-" : u.getLoginId()));
        return logs.stream()
                .map(a -> new AuditLogResponse(
                        a.getId(), a.getAdminId(), loginById.getOrDefault(a.getAdminId(), "-"),
                        a.getAction(), a.getTargetType(), a.getTargetId(), a.getDetail(),
                        a.getIpAddr(), a.getCreatedAt()))
                .toList();
    }
}
