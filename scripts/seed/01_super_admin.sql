-- 첫 관리자(SUPER_ADMIN) 부트스트랩
-- 관리자에는 공개 회원가입이 없으므로 최초 1명은 DB로 직접 시드한다.
-- 이후 관리자는 이 계정으로 로그인해 POST /api/admin/admin-users (SUPER_ADMIN 전용)로 생성한다.
--
-- 로그인 계정 : admin
--
-- 비밀번호는 이 스크립트에 넣지 않는다. 실행하는 사람이 직접 해시를 만들어 채운다.
-- 고정 해시를 커밋하면 저장소를 읽은 누구나 아는 자격증명으로 최고관리자가 생성된다.
--
-- 해시 생성: 이 프로젝트의 테스트 소스에서 한 번 실행한다.
--   new BCryptPasswordEncoder(12).encode("<원하는 비밀번호>")
--   cost 12는 PasswordConfig.passwordEncoder()와 같은 값이다.
--   결과는 $2a$12$ 로 시작하는 60자 문자열이다.
--
-- 실행: 아래 :PASSWORD_HASH 를 생성한 해시로 치환한 뒤
--       SQL Developer 등에서 대상 DB(로컬 XE 또는 클라우드 ADB)에 실행한다.
--
-- 이미 이 스크립트의 이전 판(고정 해시 포함)으로 배포한 적이 있으면
-- 그 계정의 비밀번호를 먼저 교체한다.

INSERT INTO ADMIN_USER (LOGIN_ID, PASSWORD_HASH, PW_ALGO, NAME, ROLE, STATUS, FAILED_LOGIN_CNT, OTP_ENABLED, CREATED_AT, UPDATED_AT)
VALUES (
    'admin',
    ':PASSWORD_HASH',
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
