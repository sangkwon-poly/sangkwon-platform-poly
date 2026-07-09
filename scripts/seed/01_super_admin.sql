-- 첫 관리자(SUPER_ADMIN) 부트스트랩
-- 관리자에는 공개 회원가입이 없으므로 최초 1명은 DB로 직접 시드한다.
-- 이후 관리자는 이 계정으로 로그인해 POST /api/admin/admin-users (SUPER_ADMIN 전용)로 생성한다.
--
-- 로그인 계정 : admin
-- 초기 비밀번호: 1234   (BCrypt 해시로 저장됨 · 로그인 후 즉시 변경 권장)
--
-- 실행: SQL Developer 등에서 이 스크립트를 대상 DB(로컬 XE 또는 클라우드 ADB)에 실행.

INSERT INTO ADMIN_USER (LOGIN_ID, PASSWORD_HASH, PW_ALGO, NAME, ROLE, STATUS, FAILED_LOGIN_CNT, OTP_ENABLED, CREATED_AT, UPDATED_AT)
VALUES (
    'admin',
    '$2a$10$IcLx/9Rq6zshW9RYk1JAe.0GU2XsqVrtvNfCqlkjGCXJnusVCR1hu',
    'BCRYPT',
    '최고관리자',
    'SUPER_ADMIN',
    'ACTIVE',
    0,
    'N',
    SYSTIMESTAMP,
    SYSTIMESTAMP
);
COMMIT;
