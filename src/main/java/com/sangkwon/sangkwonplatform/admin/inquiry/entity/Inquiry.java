package com.sangkwon.sangkwonplatform.admin.inquiry.entity;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;
import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "INQUIRY")
public class Inquiry extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id", length = 19)
    private Long inquiryId;

    // 문의 작성 회원. 회원 하드삭제 시 스키마 FK가 ON DELETE SET NULL이라 null이 될 수 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private InquiryStatus status = InquiryStatus.OPEN;

    @Lob
    @Column(name = "answer")
    private String answer;

    // 답변 관리자. 스키마 컬럼명은 ANSWERED_BY.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by")
    private AdminUser admin;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    // 답변 등록: CK_INQ_ANSWERED(답변·답변자·답변시각 동시 필수)를 만족시키며 상태를 ANSWERED로 전이한다.
    public void answerBy(AdminUser admin, String answer) {
        this.admin = admin;
        this.answer = answer;
        this.answeredAt = LocalDateTime.now();
        this.status = InquiryStatus.ANSWERED;
    }

    public void close() {
        this.status = InquiryStatus.CLOSED;
    }
}
