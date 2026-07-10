package com.sangkwon.sangkwonplatform.admin.trdar.controller;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.account.session.LoginAdmin;
import com.sangkwon.sangkwonplatform.admin.trdar.dto.TrdarDetailResponse;
import com.sangkwon.sangkwonplatform.admin.trdar.dto.TrdarHealthResponse;
import com.sangkwon.sangkwonplatform.admin.trdar.service.AdminTrdarService;
import com.sangkwon.sangkwonplatform.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 상권 데이터 백오피스: 완결성/품질 점검, 상권 상세. /api/admin/** 는 인터셉터가 로그인 보장.
// CSV 내보내기는 이미 로드된 목록으로 클라이언트에서 처리한다(자치구 필터가 이름 기반이라 서버 코드 필터와 안 맞음).
@RestController
@RequestMapping("/api/admin/trdar")
@RequiredArgsConstructor
public class AdminTrdarController {

    private final AdminTrdarService adminTrdarService;

    // 분기별 팩트 완결성 + 품질 플래그
    @GetMapping("/health")
    public ApiResponse<TrdarHealthResponse> health(@LoginAdmin AdminSession admin,
                                                   @RequestParam(required = false) String quarter) {
        return ApiResponse.ok(adminTrdarService.health(quarter));
    }

    // 상권 상세: 지표 + 업종별 매출 + 분기 추이
    @GetMapping("/{trdarCd}")
    public ApiResponse<TrdarDetailResponse> detail(@LoginAdmin AdminSession admin,
                                                   @PathVariable String trdarCd,
                                                   @RequestParam(required = false) String quarter) {
        return ApiResponse.ok(adminTrdarService.detail(trdarCd, quarter));
    }
}
