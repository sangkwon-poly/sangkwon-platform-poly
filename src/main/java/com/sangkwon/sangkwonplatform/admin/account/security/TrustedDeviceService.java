package com.sangkwon.sangkwonplatform.admin.account.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * "이 기기 신뢰" 토큰. 서버가 HMAC-SHA256으로 서명한 무상태 쿠키로,
 * 유효한 토큰을 가진 기기는 다음 로그인에서 OTP 단계를 건너뛴다.
 * 토큰은 특정 adminId에만 유효하며 만료 시각이 박혀 있다.
 */
@Service
public class TrustedDeviceService {

    public static final String COOKIE_NAME = "ADMIN_TRUST";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;
    private final int days;

    public TrustedDeviceService(
            @Value("${admin.security.trust-device-secret:}") String secretProp,
            @Value("${admin.security.trust-device-days:30}") int days,
            Environment env) {
        this.days = days;
        if (secretProp != null && !secretProp.isBlank()) {
            this.secret = secretProp.getBytes(StandardCharsets.UTF_8);
        } else {
            // 미설정 시 부팅마다 임의 키를 쓴다(재시작하면 기존 신뢰 쿠키는 무효).
            // 다중 인스턴스에서는 인스턴스마다 키가 달라 신뢰 쿠키가 상호 거부되고 OTP가 들쭉날쭉해진다.
            // prod는 반드시 공유 시크릿(ADMIN_TRUST_DEVICE_SECRET)을 주입하도록 기동 시 강제한다.
            if (env.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(
                        "prod에는 admin.security.trust-device-secret(ADMIN_TRUST_DEVICE_SECRET)이 필요합니다. "
                                + "인스턴스마다 랜덤 키가 생기면 다중 인스턴스 신뢰기기가 상호 거부됩니다.");
            }
            byte[] rnd = new byte[32];
            new SecureRandom().nextBytes(rnd);
            this.secret = rnd;
        }
    }

    public int days() {
        return days;
    }

    /** 관리자, 비밀번호 버전, 현재 OTP 비밀키에 묶인 신뢰 토큰 발급. */
    public String issue(Long adminId, int pwVersion, String otpSecret) {
        if (adminId == null || otpSecret == null || otpSecret.isBlank()) {
            throw new IllegalArgumentException("OTP가 활성화된 관리자만 신뢰 토큰을 발급할 수 있습니다.");
        }
        long exp = System.currentTimeMillis() + days * 86_400_000L;
        String payload = adminId + ":" + pwVersion + ":" + otpBinding(otpSecret) + ":" + exp;
        return B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload);
    }

    /** 토큰이 현재 관리자 인증 상태에 대해 유효하고 만료되지 않았는지 검증. */
    public boolean verify(String token, Long adminId, int pwVersion, String otpSecret) {
        if (token == null || adminId == null || otpSecret == null || otpSecret.isBlank()) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return false;
        }
        String payload;
        try {
            payload = new String(B64D.decode(token.substring(0, dot)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!constantTimeEquals(token.substring(dot + 1), sign(payload))) {
            return false;
        }
        String[] parts = payload.split(":");
        if (parts.length != 4
                || !adminId.toString().equals(parts[0])
                || !Integer.toString(pwVersion).equals(parts[1])
                || !constantTimeEquals(parts[2], otpBinding(otpSecret))) {
            return false;
        }
        try {
            return Long.parseLong(parts[3]) > System.currentTimeMillis();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String otpBinding(String otpSecret) {
        return sign("otp:" + otpSecret);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            return B64.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("신뢰 기기 토큰 서명 실패", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
