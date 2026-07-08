package com.sangkwon.sangkwonplatform.admin.account.interceptor;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminIpInterceptorTest {

    private final HttpServletResponse response = new MockHttpServletResponse();

    private MockHttpServletRequest request(String xff, String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (xff != null) {
            req.addHeader("X-Forwarded-For", xff);
        }
        req.setRemoteAddr(remoteAddr);
        return req;
    }

    @Test
    void 허용목록이_비면_모두_통과() {
        AdminIpInterceptor interceptor = new AdminIpInterceptor("", true);
        assertThat(interceptor.preHandle(request("1.2.3.4", "9.9.9.9"), response, new Object())).isTrue();
    }

    @Test
    void XFF는_가장_오른쪽_프록시가_찍은_값으로_판정한다() {
        AdminIpInterceptor interceptor = new AdminIpInterceptor("10.0.0.5", true);
        // 프록시가 실제 접속 IP(10.0.0.5)를 오른쪽에 붙인 정상 요청
        assertThat(interceptor.preHandle(request("203.0.113.9, 10.0.0.5", "10.0.0.5"), response, new Object())).isTrue();
    }

    @Test
    void 왼쪽에_허용IP를_위조해도_차단된다() {
        AdminIpInterceptor interceptor = new AdminIpInterceptor("10.0.0.5", true);
        // 공격자가 허용 IP를 왼쪽에 끼워넣어도 오른쪽(실제 9.9.9.9) 기준이라 403
        assertThatThrownBy(() -> interceptor.preHandle(request("10.0.0.5, 9.9.9.9", "9.9.9.9"), response, new Object()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void XFF를_신뢰하지_않으면_원격주소로_판정한다() {
        AdminIpInterceptor interceptor = new AdminIpInterceptor("10.0.0.5", false);
        assertThat(interceptor.preHandle(request("9.9.9.9", "10.0.0.5"), response, new Object())).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request("10.0.0.5", "9.9.9.9"), response, new Object()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
