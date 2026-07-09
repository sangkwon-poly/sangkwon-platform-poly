package com.sangkwon.sangkwonplatform.admin.account.otp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    // RFC 6238 표준 테스트 시드 "12345678901234567890"(ASCII)의 Base32 표현
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void RFC6238_표준_벡터와_일치한다() {
        // 6자리 = 표준 8자리 TOTP의 하위 6자리
        assertThat(Totp.generate(SECRET, 59L / 30)).isEqualTo("287082");
        assertThat(Totp.generate(SECRET, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.generate(SECRET, 1111111111L / 30)).isEqualTo("050471");
        assertThat(Totp.generate(SECRET, 1234567890L / 30)).isEqualTo("005924");
        assertThat(Totp.generate(SECRET, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    void verify는_현재와_앞뒤_한스텝을_허용한다() {
        assertThat(Totp.verify(SECRET, "287082", 59)).isTrue();
        assertThat(Totp.verify(SECRET, "287082", 59 + 30)).isTrue();
        assertThat(Totp.verify(SECRET, "287082", 59 - 30)).isTrue();
        assertThat(Totp.verify(SECRET, "287082", 59 + 60)).isFalse(); // 2스텝 밖
    }

    @Test
    void 잘못된_코드와_형식은_거부한다() {
        assertThat(Totp.verify(SECRET, "000000", 59)).isFalse();
        assertThat(Totp.verify(SECRET, "28708", 59)).isFalse();  // 5자리
        assertThat(Totp.verify(SECRET, null, 59)).isFalse();
        assertThat(Totp.verify(null, "287082", 59)).isFalse();
    }

    @Test
    void 발급한_비밀키는_생성_검증이_왕복된다() {
        String secret = Totp.generateSecret();
        String code = Totp.generate(secret, 100);
        assertThat(Totp.verify(secret, code, 100 * 30)).isTrue();
    }

    @Test
    void matchedStep은_코드의_시간스텝을_돌려주고_틀리면_MIN_VALUE() {
        // 59초 -> 스텝 1. 앞뒤 한 스텝 창 안에서도 매칭된 코드의 스텝(1)을 돌려준다
        assertThat(Totp.matchedStep(SECRET, "287082", 59)).isEqualTo(1L);
        assertThat(Totp.matchedStep(SECRET, "287082", 59 + 30)).isEqualTo(1L);
        assertThat(Totp.matchedStep(SECRET, "000000", 59)).isEqualTo(Long.MIN_VALUE);
        assertThat(Totp.matchedStep(SECRET, "28708", 59)).isEqualTo(Long.MIN_VALUE);
    }
}
