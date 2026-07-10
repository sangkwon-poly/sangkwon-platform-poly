package com.sangkwon.sangkwonplatform.member.dto.response;

// 결제위젯 렌더에 필요한 공개 설정. clientKey는 공개 키라 노출해도 된다(승인은 서버의 시크릿 키로만 가능).
public record PaymentConfigResponse(
        String clientKey
) {
}
