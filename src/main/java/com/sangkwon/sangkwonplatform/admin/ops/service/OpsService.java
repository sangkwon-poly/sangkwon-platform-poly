package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.ops.dto.ApiUsageResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.AuditLogResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.BatchLogResponse;
import com.sangkwon.sangkwonplatform.admin.ops.entity.AdminAuditLog;
import com.sangkwon.sangkwonplatform.admin.ops.repository.AdminAuditLogRepository;
import com.sangkwon.sangkwonplatform.admin.ops.repository.ApiUsageLogRepository;
import com.sangkwon.sangkwonplatform.global.batch.BatchJobLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
