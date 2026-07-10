package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.ops.ExternalApi;
import com.sangkwon.sangkwonplatform.admin.ops.dto.ApiUsageResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.AuditLogResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.AuditPageResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

    // 집계 행이 아직 없어도 계측 중인 API는 0건으로 함께 보여준다. 오늘 안 썼다는 사실도 운영 정보다.
    public List<ApiUsageResponse> todayApiUsage() {
        LocalDate today = LocalDate.now();
        Map<String, ApiUsageResponse> byName = new TreeMap<>();
        for (ExternalApi api : ExternalApi.values()) {
            byName.put(api.name(), new ApiUsageResponse(api.name(), today, 0, api.dailyLimit(), 0));
        }
        apiUsageLogRepository.findByUsageDateOrderByApiName(today)
                .forEach(a -> byName.put(a.getApiName(), ApiUsageResponse.from(a)));
        return List.copyOf(byName.values());
    }

    // 감사 로그: 행위·행위자(adminId)·대상 필터(모두 선택) + 페이징. 관리자 로그인 아이디는 한 번에 모아 매핑한다.
    public AuditPageResponse searchAudits(String action, Long adminId, String targetType, String targetId, Pageable pageable) {
        Page<AdminAuditLog> logs = adminAuditLogRepository.search(
                blankToNull(action), adminId, blankToNull(targetType), blankToNull(targetId), pageable);
        Map<Long, String> loginById = adminUserRepository.findAllById(
                        logs.getContent().stream().map(AdminAuditLog::getAdminId).distinct().toList())
                .stream().collect(Collectors.toMap(AdminUser::getAdminId,
                        u -> u.getLoginId() == null ? "-" : u.getLoginId()));
        List<AuditLogResponse> content = logs.getContent().stream()
                .map(a -> new AuditLogResponse(
                        a.getId(), a.getAdminId(), loginById.getOrDefault(a.getAdminId(), "-"),
                        a.getAction(), a.getTargetType(), a.getTargetId(), a.getDetail(),
                        a.getIpAddr(), a.getCreatedAt()))
                .toList();
        return new AuditPageResponse(content, logs.getNumber(), logs.getSize(),
                logs.getTotalElements(), logs.getTotalPages());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
