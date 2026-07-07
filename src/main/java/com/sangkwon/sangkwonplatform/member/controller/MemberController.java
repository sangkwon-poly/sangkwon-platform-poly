package com.sangkwon.sangkwonplatform.member.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberSignupRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberUpdateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.MemberResponse;
import com.sangkwon.sangkwonplatform.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    public ApiResponse<MemberResponse> signup(@Valid @RequestBody MemberSignupRequest req) {
        return ApiResponse.ok(memberService.signup(req));
    }

    @GetMapping("/me")
    public ApiResponse<MemberResponse> me(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(memberService.getMe(memberId));
    }

    @PatchMapping("/me")
    public ApiResponse<MemberResponse> update(@AuthenticationPrincipal Long memberId,
                                              @Valid @RequestBody MemberUpdateRequest req) {
        return ApiResponse.ok(memberService.updateMe(memberId, req));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long memberId) {
        memberService.withdraw(memberId);
        return ApiResponse.<Void>ok(null);
    }
}
