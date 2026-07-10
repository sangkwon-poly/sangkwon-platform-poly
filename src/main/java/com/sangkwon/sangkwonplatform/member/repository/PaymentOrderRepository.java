package com.sangkwon.sangkwonplatform.member.repository;

import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {

    // 본인 주문만 승인 가능하도록 회원 조건을 함께 건다
    Optional<PaymentOrder> findByOrderIdAndMemberId(String orderId, Long memberId);
}
