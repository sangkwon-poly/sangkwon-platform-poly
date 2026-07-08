package com.sangkwon.sangkwonplatform.admin.notice.entity;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.IsPinned;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.NoticeStatus;
import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "NOTICE")
@Getter
@Setter
public class Notice extends BaseEntity {
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
}
