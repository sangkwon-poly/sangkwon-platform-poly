package com.sangkwon.sangkwonplatform.admin.notice.dto.response;

import com.sangkwon.sangkwonplatform.admin.notice.entity.Notice;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.IsPinned;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.NoticeStatus;

import java.time.LocalDateTime;

public record NoticeDetailResponse(
       Long noticeId,
       String title,
       String content,
       IsPinned isPinned,
       int viewCnt,
       String adminName,
       LocalDateTime createdAt,
       LocalDateTime updatedAt

) {
    public static NoticeDetailResponse from (Notice notice){
        return new NoticeDetailResponse(
                notice.getNoticeId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getIsPinned(),
                notice.getViewCnt(),
                notice.getAdmin().getAdminName(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()

        );
    }

}
