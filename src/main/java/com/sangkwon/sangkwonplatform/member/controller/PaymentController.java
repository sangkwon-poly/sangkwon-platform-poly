package com.sangkwon.sangkwonplatform.member.controller;

import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import com.sangkwon.sangkwonplatform.member.dto.request.PaymentConfirmRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.PaymentOrderCreateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.PaymentConfirmResponse;
import com.sangkwon.sangkwonplatform.member.dto.response.PaymentOrderResponse;
import com.sangkwon.sangkwonplatform.member.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 요금제 결제: 주문 생성과 승인. 둘 다 로그인 회원 전용(비로그인은 서비스에서 401).
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/orders")
    public ApiResponse<PaymentOrderResponse> createOrder(@AuthenticationPrincipal Long memberId,
                                                         @Valid @RequestBody PaymentOrderCreateRequest request) {
        return ApiResponse.ok(paymentService.createOrder(memberId, request));
    }

    @PostMapping("/confirm")
    public ApiResponse<PaymentConfirmResponse> confirm(@AuthenticationPrincipal Long memberId,
                                                       @Valid @RequestBody PaymentConfirmRequest request) {
        return ApiResponse.ok(paymentService.confirm(memberId, request));
    }
}
