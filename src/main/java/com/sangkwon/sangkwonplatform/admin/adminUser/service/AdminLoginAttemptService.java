package com.sangkwon.sangkwonplatform.admin.adminUser.service;

import com.sangkwon.sangkwonplatform.admin.adminUser.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.adminUser.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 로그인 실패 기록은 로그인 트랜잭션과 분리(REQUIRES_NEW)해, 로그인 메서드가 예외로 롤백돼도 실패 카운트/잠금이 확정되게 한다.
@Service
@RequiredArgsConstructor
public class AdminLoginAttemptService {

    // 연속 실패 임계치 (초과 시 계정 잠금)
    public static final int MAX_FAILED = 5;

    private final AdminUserRepository adminUserRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long adminId) {
        if (adminId == null) {
            return;
        }
        adminUserRepository.findById(adminId).ifPresent(admin -> {
            admin.increaseFailedLoginCnt();
            if (admin.getFailedLoginCnt() >= MAX_FAILED) {
                admin.updateStatus(AdminStatus.LOCKED);
            }
        });
    }
}
