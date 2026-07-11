package com.sangkwon.sangkwonplatform.member.repository;

import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {

    // 방치 결제 정리: 결제창만 진입하고 승인 안 된 채 오래된 PENDING 주문을 FAILED로 마킹(중단된 결제).
    @Transactional
    @Modifying
    @Query("""
            update PaymentOrder o set o.status = com.sangkwon.sangkwonplatform.member.entity.PaymentStatus.FAILED
            where o.status = com.sangkwon.sangkwonplatform.member.entity.PaymentStatus.PENDING
              and o.createdAt < :cutoff
            """)
    int markStalePendingAsFailed(@Param("cutoff") LocalDateTime cutoff);

    // 본인 주문만 승인 가능하도록 회원 조건을 함께 건다
    Optional<PaymentOrder> findByOrderIdAndMemberId(String orderId, Long memberId);

    // 관리자 회원 상세: 특정 회원의 결제 이력(최신순)
    List<PaymentOrder> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    // 관리자 결제 목록: 상태·주기 필터(모두 선택). 회원 검색이 있으면 아래 IN 버전을 쓴다.
    @Query("""
            select o from PaymentOrder o
            where (:status is null or o.status = :status)
              and (:cycle is null or o.billingCycle = :cycle)
            """)
    Page<PaymentOrder> searchForAdmin(@Param("status") PaymentStatus status,
                                      @Param("cycle") BillingCycle cycle,
                                      Pageable pageable);

    // 검색에 걸린 회원의 주문으로 좁힌다. 빈 ID 목록은 서비스에서 미리 걸러 여기로 넘기지 않는다.
    @Query("""
            select o from PaymentOrder o
            where (:status is null or o.status = :status)
              and (:cycle is null or o.billingCycle = :cycle)
              and o.memberId in :memberIds
            """)
    Page<PaymentOrder> searchForAdminByMembers(@Param("status") PaymentStatus status,
                                               @Param("cycle") BillingCycle cycle,
                                               @Param("memberIds") List<Long> memberIds,
                                               Pageable pageable);

    // 상태 필터 칩 카운트용. 없는 상태는 행이 안 나오므로 서비스에서 0으로 채운다.
    @Query("select o.status as status, count(o) as cnt from PaymentOrder o group by o.status")
    List<PaymentStatusCount> countGroupByStatus();

    interface PaymentStatusCount {
        PaymentStatus getStatus();

        long getCnt();
    }

    // 기간 매출: 승인 시각 기준 금액 합계(원)
    @Query("""
            select coalesce(sum(o.amount), 0) from PaymentOrder o
            where o.status = :status and o.approvedAt >= :from
            """)
    long sumAmountByStatusSince(@Param("status") PaymentStatus status, @Param("from") LocalDateTime from);

    long countByStatusAndApprovedAtGreaterThanEqual(PaymentStatus status, LocalDateTime from);
}
