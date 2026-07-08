-- ADMIN_USER 에 OTP_LAST_STEP 컬럼 추가. 이미 만들어진 DB에 적용하는 마이그레이션
-- 주의: 이 프로젝트는 spring.jpa.hibernate.ddl-auto=none 이므로 앱이 스키마를 만들지 않습니다.
--    OTP 리플레이 방지(같은 코드 재사용 차단)를 쓰는 새 코드를 올리기 전에 이 스크립트를 먼저 실행하세요.
--
-- 컬럼은 추가(additive)라 기존 코드/쿼리에는 영향이 없습니다.

ALTER TABLE ADMIN_USER ADD (
    OTP_LAST_STEP NUMBER(19)
);

COMMENT ON COLUMN ADMIN_USER.OTP_LAST_STEP IS '마지막으로 소비한 TOTP 시간 스텝(리플레이 방지). 사용 이력 없으면 NULL';
