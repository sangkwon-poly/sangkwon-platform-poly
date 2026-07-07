package com.sangkwon.sangkwonplatform.admin.notice.dto.response;

import com.sangkwon.sangkwonplatform.admin.notice.entity.Notice;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.IsPinned;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.NoticeStatus;

import java.time.LocalDateTime;

public record NoticeAdminListResponse(
        Long noticeId,
        String title,
        Long adminId,
        String adminName,
        IsPinned isPinned,
        int viewCnt,
        NoticeStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NoticeAdminListResponse from (Notice notice){
        return new NoticeAdminListResponse(
                notice.getNoticeId(),
                notice.getTitle(),
                notice.getAdmin().getAdminId(),
                notice.getAdmin().getAdminName(),
                notice.getIsPinned(),
                notice.getViewCnt(),
                notice.getStatus(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()

        );
    }
}
