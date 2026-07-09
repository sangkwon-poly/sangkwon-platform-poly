package com.sangkwon.sangkwonplatform.admin.account.service;

import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.service.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// 로그인 실패 기록은 로그인 트랜잭션과 분리(REQUIRES_NEW)해, 로그인 메서드가 예외로 롤백돼도 실패 카운트/잠금/감사가 확정되게 한다.
@Service
@RequiredArgsConstructor
public class AdminLoginAttemptService {

    // 연속 실패 임계치 (초과 시 계정 잠금)
    public static final int MAX_FAILED = 5;

    private final AdminUserRepository adminUserRepository;
    private final AdminAuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long adminId) {
        if (adminId == null) {
            return;
        }
        adminUserRepository.findById(adminId).ifPresent(admin -> {
            admin.increaseFailedLoginCnt();
            boolean locked = admin.getFailedLoginCnt() >= MAX_FAILED;
            if (locked) {
                admin.updateStatus(AdminStatus.LOCKED);
            }
            // 무차별 대입 시도를 감사 로그에서 추적할 수 있도록 실패/잠금을 남긴다(IP는 현재 요청에서 얻는다)
            HttpServletRequest request = currentRequest();
            auditService.record(adminId, AuditAction.LOGIN_FAILED, null, null,
                    "실패 " + admin.getFailedLoginCnt() + "회", request);
            if (locked) {
                auditService.record(adminId, AuditAction.ACCOUNT_LOCKED, "ADMIN",
                        String.valueOf(adminId), "연속 실패로 계정 잠금", request);
            }
        });
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes sra) ? sra.getRequest() : null;
    }
}
