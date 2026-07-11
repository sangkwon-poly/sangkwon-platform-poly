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

    // 회원이 답변을 처음 열람한 시각. null이면 미확인(회원에게 새 답변 알림 표시).
    @Column(name = "answer_read_at")
    private LocalDateTime answerReadAt;

    // 동시 답변 경합 방지용 낙관적 락. 두 관리자가 같은 문의에 동시 답변하면 뒤진 저장이 실패한다(덮어쓰기 방지).
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // 답변 등록: CK_INQ_ANSWERED(답변·답변자·답변시각 동시 필수)를 만족시키며 상태를 ANSWERED로 전이한다.
    public void answerBy(AdminUser admin, String answer) {
        this.admin = admin;
        this.answer = answer;
        this.answeredAt = LocalDateTime.now();
        this.status = InquiryStatus.ANSWERED;
    }

    // 회원이 아직 확인하지 않은 답변인가(새 답변 알림 대상).
    public boolean isAnswerUnread() {
        return this.status == InquiryStatus.ANSWERED && this.answerReadAt == null;
    }

    public void close() {
        this.status = InquiryStatus.CLOSED;
    }
}
