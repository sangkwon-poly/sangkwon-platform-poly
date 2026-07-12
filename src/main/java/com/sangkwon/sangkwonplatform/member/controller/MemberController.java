package com.sangkwon.sangkwonplatform.member.controller;

import com.sangkwon.sangkwonplatform.admin.account.security.ClientIpResolver;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.global.security.DbRateLimiter;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberSignupRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.MemberUpdateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.AvailabilityResponse;
import com.sangkwon.sangkwonplatform.member.dto.response.MemberResponse;
import com.sangkwon.sangkwonplatform.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    // 가용성 조회 남용(계정 열거 스캔) 방지. 정상 가입 폼은 몇 번만 호출하므로 넉넉히 잡되 스캔은 막는다.
    private static final int AVAIL_MAX = 30;
    private static final Duration AVAIL_WINDOW = Duration.ofMinutes(1);

    private final MemberService memberService;
    private final DbRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    public ApiResponse<MemberResponse> signup(@Valid @RequestBody MemberSignupRequest req) {
        return ApiResponse.ok(memberService.signup(req));
    }

    // 가입 화면 실시간 중복 확인. 미인증 공개 엔드포인트라 IP별 레이트리밋으로 계정 열거 스캔을 막는다.
    @GetMapping("/check-login-id")
    public ApiResponse<AvailabilityResponse> checkLoginId(@RequestParam String loginId, HttpServletRequest request) {
        throttleAvailability(request);
        return ApiResponse.ok(new AvailabilityResponse(memberService.isLoginIdAvailable(loginId)));
    }

    @GetMapping("/check-email")
    public ApiResponse<AvailabilityResponse> checkEmail(@RequestParam String email, HttpServletRequest request) {
        throttleAvailability(request);
        return ApiResponse.ok(new AvailabilityResponse(memberService.isEmailAvailable(email)));
    }

    // 미인증 가용성 조회를 IP별로 제한한다(공유 DB 집계라 다중 인스턴스에서도 유효). 초과하면 429.
    private void throttleAvailability(HttpServletRequest request) {
        String key = "member-avail:" + clientIpResolver.resolve(request);
        if (rateLimiter.isBlocked(key, AVAIL_MAX, AVAIL_WINDOW)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        rateLimiter.record(key);
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
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long memberId, HttpServletRequest request) {
        memberService.withdraw(memberId);
        // 탈퇴 후 남아있는 세션으로 계속 접근하지 못하도록 세션을 무효화한다
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.<Void>ok(null);
    }
}
