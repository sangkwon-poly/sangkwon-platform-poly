package com.sangkwon.sangkwonplatform.member.controller;

import com.sangkwon.sangkwonplatform.admin.account.security.ClientIpResolver;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.member.dto.request.SearchLogCreateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.SearchLogResponse;
import com.sangkwon.sangkwonplatform.member.service.SearchLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-logs")
@RequiredArgsConstructor
public class SearchLogController {

    private final SearchLogService searchLogService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping
    public ApiResponse<List<SearchLogResponse>> recent(@AuthenticationPrincipal Long memberId,
                                                       @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(searchLogService.recent(memberId, limit));
    }

    @PostMapping
    public ApiResponse<Void> log(@AuthenticationPrincipal Long memberId,
                                 @Valid @RequestBody SearchLogCreateRequest req,
                                 HttpServletRequest httpRequest) {
        searchLogService.log(memberId, req, clientIpResolver.resolve(httpRequest));
        return ApiResponse.<Void>ok(null);
    }

    @DeleteMapping("/{keyword}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal Long memberId,
                                    @PathVariable String keyword) {
        searchLogService.delete(memberId, keyword);
        return ApiResponse.<Void>ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> removeAll(@AuthenticationPrincipal Long memberId) {
        searchLogService.deleteAll(memberId);
        return ApiResponse.<Void>ok(null);
    }
}
