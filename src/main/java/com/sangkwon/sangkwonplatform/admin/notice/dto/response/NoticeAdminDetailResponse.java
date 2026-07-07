package com.sangkwon.sangkwonplatform.admin.notice.dto.response;

import com.sangkwon.sangkwonplatform.admin.notice.entity.Notice;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.NoticeStatus;

public record NoticeAdminDetailResponse(
        Long noticeId,
        String title,
        String content,
        Long adminId,
        String adminName,
        NoticeStatus status
) {
    public static NoticeAdminDetailResponse from(Notice notice) {
        return new NoticeAdminDetailResponse(
                notice.getNoticeId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getAdmin().getAdminId(),
                notice.getAdmin().getAdminName(),
                notice.getStatus()
        );
    }
}
