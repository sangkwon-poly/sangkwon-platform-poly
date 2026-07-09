package com.sangkwon.sangkwonplatform.admin.account.otp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 6자리, 30초 주기). Google Authenticator 등 표준 인증 앱과 호환된다.
 * 외부 라이브러리 없이 순수 자바로 구현.
 */
public final class Totp {

    private Totp() {
    }

    private static final int DIGITS = 6;
    private static final long PERIOD = 30;
    private static final int WINDOW = 1; // 앞뒤 1스텝 허용(시계 오차 대비)
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    // 20바이트(160비트) 랜덤 → Base32 비밀키
    public static String generateSecret() {
        byte[] buf = new byte[20];
        RANDOM.nextBytes(buf);
        return base32Encode(buf);
    }

    // 인증 앱에 등록할 otpauth:// URL (QR로 만들어 스캔)
    public static String otpauthUrl(String issuer, String account, String secret) {
        String iss = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String acc = URLEncoder.encode(account, StandardCharsets.UTF_8);
        return "otpauth://totp/" + iss + ":" + acc
                + "?secret=" + secret + "&issuer=" + iss + "&digits=" + DIGITS + "&period=" + PERIOD;
    }

    public static boolean verify(String secret, String code) {
        return matchedStep(secret, code) != Long.MIN_VALUE;
    }

    static boolean verify(String secret, String code, long epochSeconds) {
        return matchedStep(secret, code, epochSeconds) != Long.MIN_VALUE;
    }

    // 코드가 맞으면 일치한 시간 스텝을, 아니면 Long.MIN_VALUE를 돌려준다.
    // 호출부는 이 스텝을 저장해 같은 코드의 재사용(리플레이)을 막는다.
    public static long matchedStep(String secret, String code) {
        return matchedStep(secret, code, System.currentTimeMillis() / 1000L);
    }

    static long matchedStep(String secret, String code, long epochSeconds) {
        if (secret == null || code == null) {
            return Long.MIN_VALUE;
        }
        String c = code.trim();
        if (c.length() != DIGITS) {
            return Long.MIN_VALUE;
        }
        long step = epochSeconds / PERIOD;
        for (long w = -WINDOW; w <= WINDOW; w++) {
            long candidate = step + w;
            if (constantTimeEquals(c, generate(secret, candidate))) {
                return candidate;
            }
        }
        return Long.MIN_VALUE;
    }

    // 코드 비교는 상수 시간으로 한다(String.equals는 첫 불일치에서 끊겨 타이밍이 새어 나간다)
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static String generate(String base32Secret, long counter) {
        byte[] key = base32Decode(base32Secret);
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (counter & 0xFF);
            counter >>>= 8;
        }
        byte[] hash = hmacSha1(key, data);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % 1_000_000;
        return String.format("%06d", otp);
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP 계산 실패", e);
        }
    }

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    private static byte[] base32Decode(String s) {
        String clean = s.trim().replace("=", "").toUpperCase();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char ch : clean.toCharArray()) {
            int val = BASE32.indexOf(ch);
            if (val < 0) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
