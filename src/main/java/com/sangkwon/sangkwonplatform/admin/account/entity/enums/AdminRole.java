package com.sangkwon.sangkwonplatform.admin.account.entity.enums;

public enum AdminRole {
    SUPER_ADMIN(2),
    OPERATOR(1),
    VIEWER(0);

    // 권한 세기(클수록 강함). 선언 순서(ordinal)는 SUPER_ADMIN이 0이라 그대로 쓰면 뒤집히므로 명시한다.
    private final int level;

    AdminRole(int level) {
        this.level = level;
    }

    // 이 역할이 요구 역할 이상인지. 인가 판정을 한 곳에서 일관되게 쓴다.
    public boolean atLeast(AdminRole required) {
        return this.level >= required.level;
    }
}
