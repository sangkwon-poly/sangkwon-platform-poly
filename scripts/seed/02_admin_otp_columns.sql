-- ADMIN_USER 에 OTP(2단계 인증) 컬럼 추가. 이미 만들어진 DB에 적용하는 마이그레이션
-- 주의: 이 프로젝트는 spring.jpa.hibernate.ddl-auto=none 이므로 앱이 스키마를 만들지 않습니다.
--    관리자 기능을 쓰는 새 코드를 올리기 전에 반드시 이 스크립트를 먼저 실행하세요.
--    (안 하면 엔티티엔 컬럼이 있는데 DB엔 없어서 관리자 조회가 ORA-00904로 실패합니다.)
--
-- 컬럼은 추가(additive)라 기존 코드/쿼리에는 영향이 없습니다.

ALTER TABLE ADMIN_USER ADD (
    OTP_ENABLED CHAR(1 CHAR) DEFAULT 'N' NOT NULL,
    OTP_SECRET  VARCHAR2(64 CHAR)
);

ALTER TABLE ADMIN_USER ADD CONSTRAINT CK_ADM_OTP CHECK (OTP_ENABLED IN ('Y','N'));

COMMENT ON COLUMN ADMIN_USER.OTP_ENABLED IS '2단계 인증(TOTP) 사용 여부: Y / N';
COMMENT ON COLUMN ADMIN_USER.OTP_SECRET  IS 'TOTP 비밀키(Base32), 2FA 설정 시 발급 [민감]';
