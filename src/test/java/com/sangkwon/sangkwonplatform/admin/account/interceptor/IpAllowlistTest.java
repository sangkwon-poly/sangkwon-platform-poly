package com.sangkwon.sangkwonplatform.admin.account.interceptor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpAllowlistTest {

    @Test
    void 빈_설정은_제한없음() {
        assertThat(IpAllowlist.parse("").isEmpty()).isTrue();
        assertThat(IpAllowlist.parse(null).isEmpty()).isTrue();
    }

    @Test
    void 정확한_IP_일치() {
        IpAllowlist list = IpAllowlist.parse("127.0.0.1, ::1");
        assertThat(list.isAllowed("127.0.0.1")).isTrue();
        assertThat(list.isAllowed("::1")).isTrue();
        assertThat(list.isAllowed("127.0.0.2")).isFalse();
    }

    @Test
    void CIDR_대역_일치() {
        IpAllowlist list = IpAllowlist.parse("192.168.0.0/16, 10.0.0.0/8");
        assertThat(list.isAllowed("192.168.1.5")).isTrue();
        assertThat(list.isAllowed("192.168.255.255")).isTrue();
        assertThat(list.isAllowed("10.9.8.7")).isTrue();
        assertThat(list.isAllowed("172.16.0.1")).isFalse();
        assertThat(list.isAllowed("193.168.1.5")).isFalse();
    }

    @Test
    void 잘못된_입력은_불허() {
        IpAllowlist list = IpAllowlist.parse("192.168.0.0/16");
        assertThat(list.isAllowed("not-an-ip")).isFalse();
    }
}
