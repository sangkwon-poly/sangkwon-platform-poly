package com.sangkwon.sangkwonplatform.admin.notice.controller;

import com.sangkwon.sangkwonplatform.admin.notice.dto.response.NoticeDetailResponse;
import com.sangkwon.sangkwonplatform.admin.notice.dto.response.NoticePublicPageResponse;
import com.sangkwon.sangkwonplatform.admin.notice.service.NoticeService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 공개 공지사항: 발행(PUBLISHED)된 공지만 누구나 조회. 작성·발행은 NoticeAdminController.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notices")
public class NoticePublicController {

    private static final int MAX_SIZE = 100;

    private final NoticeService noticeService;

    @GetMapping
    public ApiResponse<NoticePublicPageResponse> list(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return ApiResponse.ok(NoticePublicPageResponse.from(noticeService.getPublicList(pageable)));
    }

    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeDetailResponse> detail(@PathVariable Long noticeId) {
        return ApiResponse.ok(noticeService.getPublicDetail(noticeId));
    }
}
