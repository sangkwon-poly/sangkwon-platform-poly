package com.sangkwon.sangkwonplatform.admin.notice.dto.request;

import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.IsPinned;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeCreateRequest(

        @NotBlank(message = "공지 제목은 필수입니다!")
        @Size(max = 200, message = "공지 제목은 200자 이하로 입력해 주세요!")
        String title,

        @NotBlank(message = "공지 내용은 필수입니다!")
        String content,

        IsPinned isPinned
) {
    public IsPinned resolvedIspinned(){
        return isPinned == null ? IsPinned.N : isPinned;
    }
}
