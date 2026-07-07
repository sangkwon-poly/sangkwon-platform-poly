package com.sangkwon.sangkwonplatform.member.dto.response;

// 로그인 응답 — 프론트 api.js가 data.accessToken 을 저장해 이후 Bearer 헤더로 사용.
public record TokenResponse(String accessToken) {
}
