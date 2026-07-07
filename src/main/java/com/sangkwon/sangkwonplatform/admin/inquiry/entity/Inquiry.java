package com.sangkwon.sangkwonplatform.admin.inquiry.entity;


import com.sangkwon.sangkwonplatform.admin.adminUser.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "INQUIRY")
public class Inquiry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id", length = 19)
    private Long inquiryId;

    //@ManyToOne(fetch = FetchType.LAZY)
    //@JoinColumn(name = "member_id")
    //private Member memberId;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private AdminUser admin;

    @Column(name = "answered_at", length = 6)
    private LocalDateTime answeredAt;

    @Column(name = "created_at", nullable = false, length = 6)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, length = 6)
    private LocalDateTime updatedAt;



}
