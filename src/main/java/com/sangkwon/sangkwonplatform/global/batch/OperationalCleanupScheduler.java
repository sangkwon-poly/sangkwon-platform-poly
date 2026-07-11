package com.sangkwon.sangkwonplatform.global.batch;

import com.sangkwon.sangkwonplatform.admin.ops.repository.ApiUsageLogRepository;
import com.sangkwon.sangkwonplatform.admin.pay.service.AdminPaymentService;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 운영 정리 자동화: 방치된 결제(중단된 결제창)와 오래된 사용량 이력을 주기적으로 정리한다.
// 배치 이력 정리(BatchLogRetentionCleaner)와 같은 결의 베스트에포트 스케줄 작업.
// 감사 로그(AUDIT_LOG)는 컴플라이언스 대상이라 여기서 건드리지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationalCleanupScheduler {

    // 결제창만 진입하고 이 시간을 넘도록 승인 안 된 PENDING은 방치 결제로 보고 토스와 재확인해 정리한다.
    private static final int STALE_PENDING_HOURS = 24;
    // 한 주기에 대사할 방치 결제 상한. 백로그가 쌓여도 토스를 한 번에 과하게 호출하지 않게 한다(넘치면 다음 주기).
    private static final int MAX_RECONCILE_PER_RUN = 100;
    // 사용량 일자별 집계는 작지만 무한 증식하므로 이 일수만 보존한다.
    private static final int USAGE_RETENTION_DAYS = 365;

    private final PaymentOrderRepository paymentOrderRepository;
    private final AdminPaymentService adminPaymentService;
    private final ApiUsageLogRepository apiUsageLogRepository;

    // 매시 정각. 방치된(24시간 초과 PENDING) 결제를 토스와 재확인해 정리한다.
    // 예전에는 조회 없이 일괄 FAILED로 내렸는데, 응답이 유실된 '실제로 결제된' 주문까지 조용히 FAILED가 돼
    // 돈은 빠졌는데 Pro도 못 받는 사고가 났다. 이제 주문마다 토스 실제 상태를 조회해:
    //  - 토스 승인(DONE) → PAID 확정 + 구독 활성화(유실 결제 복구)
    //  - 토스에 기록 없음/중단/만료 → FAILED(정상적인 방치 정리)
    //  - 토스 조회 실패(네트워크 등) → 건드리지 않고 다음 주기 재시도(확인 안 된 결제를 실패로 단정하지 않는다)
    @Scheduled(cron = "0 0 * * * *")
    public void reconcileStalePendingPayments() {
        List<String> orderIds;
        try {
            orderIds = paymentOrderRepository.findStalePendingOrderIds(
                    LocalDateTime.now().minusHours(STALE_PENDING_HOURS),
                    PageRequest.of(0, MAX_RECONCILE_PER_RUN));
        } catch (Exception e) {
            log.warn("방치 결제 대사 대상 조회 실패(다음 주기에 재시도): {}", e.getMessage());
            return;
        }
        if (orderIds.isEmpty()) {
            return;
        }

        int recovered = 0;
        int failed = 0;
        int unresolved = 0;
        for (String orderId : orderIds) {
            try {
                PaymentStatus after = adminPaymentService.reconcile(orderId).after();
                if (after == PaymentStatus.PAID) {
                    recovered++;
                    log.warn("방치 결제 대사: 유실됐던 결제 {}를 PAID로 복구(구독 활성화)", orderId);
                } else if (after == PaymentStatus.FAILED) {
                    failed++;
                } else {
                    unresolved++; // 토스가 아직 진행 중이라 상태 변경 안 함 → 다음 주기 재시도
                }
            } catch (Exception e) {
                unresolved++; // 토스 조회 실패 등: 확인 안 된 결제를 실패로 단정하지 않고 다음 주기에 맡긴다
                log.warn("방치 결제 대사 실패(orderId={}, 다음 주기에 재시도): {}", orderId, e.getMessage());
            }
        }
        log.info("방치 결제 대사 완료: 대상 {}건 중 복구 {}, 실패확정 {}, 미해결 {}(주기 상한 {})",
                orderIds.size(), recovered, failed, unresolved, MAX_RECONCILE_PER_RUN);
    }

    // 매일 새벽 4시 40분(KST). 오래된 사용량 이력 정리.
    @Scheduled(cron = "0 40 4 * * *")
    public void purgeOldApiUsageLogs() {
        try {
            LocalDate cutoff = LocalDate.now().minusDays(USAGE_RETENTION_DAYS);
            int deleted = apiUsageLogRepository.deleteByUsageDateBefore(cutoff);
            if (deleted > 0) {
                log.info("오래된 사용량 이력 {}건 정리(보존 {}일)", deleted, USAGE_RETENTION_DAYS);
            }
        } catch (Exception e) {
            log.warn("사용량 이력 정리 실패(다음 주기에 재시도): {}", e.getMessage());
        }
    }
}
