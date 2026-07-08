package com.sangkwon.sangkwonplatform.admin.ops;

public enum AuditAction {
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    ACCOUNT_LOCKED,
    ADMIN_CREATE,
    ADMIN_ROLE_UPDATE,
    ADMIN_STATUS_UPDATE,
    MEMBER_STATUS_UPDATE,
    NOTICE_CREATE,
    NOTICE_UPDATE,
    NOTICE_DELETE,
    PASSWORD_RESET,
    OTP_ENABLE,
    OTP_DISABLE
}
