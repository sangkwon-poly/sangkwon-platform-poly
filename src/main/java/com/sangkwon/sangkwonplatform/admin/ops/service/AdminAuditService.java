package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.admin.account.security.ClientIpResolver;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.entity.AdminAuditLog;
import com.sangkwon.sangkwonplatform.admin.ops.repository.AdminAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ClientIpResolver clientIpResolver;

    @Transactional
    public void record(Long adminId, AuditAction action, String targetType,
                       String targetId, String detail, HttpServletRequest request) {
        if (adminId == null) {
            return;
        }
        adminAuditLogRepository.save(AdminAuditLog.of(
                adminId, action.name(), targetType, targetId, detail, clientIpResolver.resolve(request)));
    }
}
