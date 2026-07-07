package com.sangkwon.sangkwonplatform.member.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.member.dto.request.FavoriteCreateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.FavoriteResponse;
import com.sangkwon.sangkwonplatform.member.service.FavoriteService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ApiResponse<List<FavoriteResponse>> list(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(favoriteService.list(memberId));
    }

    @PostMapping
    public ApiResponse<FavoriteResponse> add(@AuthenticationPrincipal Long memberId,
                                             @Valid @RequestBody FavoriteCreateRequest req) {
        return ApiResponse.ok(favoriteService.add(memberId, req));
    }

    @DeleteMapping("/{trdarCd}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal Long memberId,
                                    @PathVariable String trdarCd) {
        favoriteService.remove(memberId, trdarCd);
        return ApiResponse.<Void>ok(null);
    }
}
