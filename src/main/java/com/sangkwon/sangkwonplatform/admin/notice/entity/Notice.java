package com.sangkwon.sangkwonplatform.admin.notice.entity;

import com.sangkwon.sangkwonplatform.admin.adminUser.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.IsPinned;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.NoticeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "NOTICE")
@Getter
@Setter
public class Notice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id", length = 19)
    private Long noticeId;

    @Column(name="title", nullable =false, length = 200)
    private String title;

    @Lob
    @Column(name="content", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private AdminUser admin;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_pinned", nullable = false, length = 1)
    private IsPinned isPinned = IsPinned.N;

    @Column(name = "view_cnt", nullable = false, length = 12)
    private int viewCnt = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private NoticeStatus status = NoticeStatus.DRAFT;

    @Column(name = "created_at", nullable = false, length = 6)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, length = 6)
    private LocalDateTime updatedAt;
}
