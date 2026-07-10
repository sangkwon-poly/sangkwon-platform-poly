package com.sangkwon.sangkwonplatform.admin.account.entity.enums;

public enum AdminStatus {
    ACTIVE,
    LOCKED,   // 연속 로그인 실패로 자동 잠김. 최고관리자가 비번 재설정으로 해제한다.
    DISABLED  // 퇴사 등으로 비활성. 로그인 불가, 감사 추적을 위해 계정은 남긴다.
}
