package com.sangkwon.sangkwonplatform.admin.member.dto.response;

import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;
import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;

// 관리자 회원 상세: 기본정보 + 결제 이력 + 문의 이력을 한 번에 묶어 CS 동선(환불·문의 확인)을 잇는다.
public record AdminMemberDetailResponse(
        AdminMemberResponse member,
        List<PaymentItem> payments,
        List<InquiryItem> inquiries
) {
    public record PaymentItem(
            String orderId,
            String orderName,
            BillingCycle billingCycle,
            long amount,
            PaymentStatus status,
            LocalDateTime createdAt,
            LocalDateTime approvedAt
    ) {
        static PaymentItem from(PaymentOrder o) {
            return new PaymentItem(o.getOrderId(), o.getOrderName(), o.getBillingCycle(),
                    o.getAmount(), o.getStatus(), o.getCreatedAt(), o.getApprovedAt());
        }
    }

    public record InquiryItem(
            Long inquiryId,
            String title,
            InquiryStatus status,
            LocalDateTime createdAt,
            LocalDateTime answeredAt
    ) {
        static InquiryItem from(Inquiry i) {
            return new InquiryItem(i.getInquiryId(), i.getTitle(), i.getStatus(),
                    i.getCreatedAt(), i.getAnsweredAt());
        }
    }

    public static AdminMemberDetailResponse from(Member member, List<PaymentOrder> payments,
                                                 List<Inquiry> inquiries) {
        return new AdminMemberDetailResponse(
                AdminMemberResponse.from(member),
                payments.stream().map(PaymentItem::from).toList(),
                inquiries.stream().map(InquiryItem::from).toList());
    }
}
