-- 첫 관리자(SUPER_ADMIN) 부트스트랩
-- 관리자에는 공개 회원가입이 없으므로 최초 1명은 DB로 직접 시드한다.
-- 이후 관리자는 이 계정으로 로그인해 POST /api/admin/admin-users (SUPER_ADMIN 전용)로 생성한다.
--
-- 로그인 계정 : superadmin
-- 초기 비밀번호: admin1234!   (BCrypt 해시로 저장됨 · 로그인 후 즉시 변경 권장)
--
-- 실행: SQL Developer 등에서 이 스크립트를 대상 DB(로컬 XE 또는 클라우드 ADB)에 실행.

INSERT INTO ADMIN_USER (LOGIN_ID, PASSWORD_HASH, PW_ALGO, NAME, ROLE, STATUS, FAILED_LOGIN_CNT, CREATED_AT, UPDATED_AT)
VALUES (
    'superadmin',
    '$2a$10$3V95evo35nbeK.QU8kAhweEbrd0RNp6OydOYWNUIHOu/DFmmh6eZG',
    'BCRYPT',
    '최고관리자',
    'SUPER_ADMIN',
    'ACTIVE',
    0,
    SYSTIMESTAMP,
    SYSTIMESTAMP
);
COMMIT;
