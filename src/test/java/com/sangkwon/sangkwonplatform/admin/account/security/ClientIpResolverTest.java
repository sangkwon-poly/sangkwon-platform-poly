package com.sangkwon.sangkwonplatform.admin.account.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

// 접속 IP 판정. 이 값이 관리자 IP 허용목록·레이트리밋과 회원 로그인 레이트리밋의 키라, 스푸핑 내성이 중요하다.
class ClientIpResolverTest {

    @Test
    void XFF를_신뢰하지_않으면_원격주소를_쓴다() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.9");
        req.addHeader("X-Forwarded-For", "1.2.3.4"); // 조작해도 신뢰 안 함

        assertThat(resolver.resolve(req)).isEqualTo("10.0.0.9");
    }

    @Test
    void XFF를_신뢰하면_가장_오른쪽_값을_써서_스푸핑을_막는다() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.9");
        // 클라이언트가 왼쪽에 가짜 IP를 넣어도, 신뢰 프록시가 실제 접속 IP를 오른쪽 끝에 덧붙인다
        req.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.7");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void XFF_신뢰여도_헤더가_없으면_원격주소를_쓴다() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.9");

        assertThat(resolver.resolve(req)).isEqualTo("10.0.0.9");
    }

    @Test
    void 요청이_null이면_null을_돌려준다() {
        assertThat(new ClientIpResolver(true).resolve(null)).isNull();
    }
}
