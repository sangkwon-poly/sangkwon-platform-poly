package com.sangkwon.sangkwonplatform.admin.notice.dto.response;

import com.sangkwon.sangkwonplatform.admin.notice.entity.Notice;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.IsPinned;

import java.time.LocalDateTime;

public record NoticeSummaryResponse(
        Long noticeId,
        String title,
        IsPinned isPinned,
        int viewCnt,
        String adminName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {
    public static NoticeSummaryResponse from (Notice notice){
        return new NoticeSummaryResponse(
                notice.getNoticeId(),
                notice.getTitle(),
                notice.getIsPinned(),
                notice.getViewCnt(),
                notice.getAdmin().getAdminName(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
