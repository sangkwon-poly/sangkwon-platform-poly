package com.sangkwon.sangkwonplatform.admin.account.interceptor;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 API 접근 허용 IP 목록. 정확한 IP와 CIDR(예: 10.0.0.0/8, 2001:db8::/32)을 지원한다.
 * 비어 있으면(설정 안 함) 제한 없음으로 간주한다.
 */
public final class IpAllowlist {

    private final List<Entry> entries;

    private IpAllowlist(List<Entry> entries) {
        this.entries = entries;
    }

    public static IpAllowlist parse(String raw) {
        List<Entry> parsed = new ArrayList<>();
        if (raw != null) {
            for (String token : raw.split(",")) {
                String t = token.trim();
                if (!t.isEmpty()) {
                    Entry e = Entry.of(t);
                    if (e != null) {
                        parsed.add(e);
                    }
                }
            }
        }
        return new IpAllowlist(parsed);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean isAllowed(String ip) {
        byte[] addr = toBytes(ip);
        if (addr == null) {
            return false;
        }
        for (Entry e : entries) {
            if (e.matches(addr)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] toBytes(String ip) {
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (Exception ex) {
            return null;
        }
    }

    private static final class Entry {
        private final byte[] base;
        private final int bits;

        private Entry(byte[] base, int bits) {
            this.base = base;
            this.bits = bits;
        }

        static Entry of(String token) {
            String ip = token;
            int bits = -1;
            int slash = token.indexOf('/');
            if (slash >= 0) {
                ip = token.substring(0, slash);
                try {
                    bits = Integer.parseInt(token.substring(slash + 1).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            byte[] base = toBytes(ip);
            if (base == null) {
                return null;
            }
            if (bits < 0) {
                bits = base.length * 8; // 단일 IP는 전체 비트 일치
            }
            if (bits > base.length * 8) {
                return null;
            }
            return new Entry(base, bits);
        }

        boolean matches(byte[] addr) {
            if (addr.length != base.length) {
                return false; // IPv4 vs IPv6 불일치
            }
            int fullBytes = bits / 8;
            int remBits = bits % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addr[i] != base[i]) {
                    return false;
                }
            }
            if (remBits > 0) {
                int mask = (0xFF << (8 - remBits)) & 0xFF;
                if ((addr[fullBytes] & mask) != (base[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
