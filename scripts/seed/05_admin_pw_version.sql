-- ADMIN_USER 에 PW_VERSION 컬럼 추가. 이미 만들어진 DB에 적용하는 마이그레이션
-- 주의: 이 프로젝트는 spring.jpa.hibernate.ddl-auto=none 이므로 앱이 스키마를 만들지 않습니다.
--    비번 변경 시 기존 세션 무효화(PW_VERSION 비교)를 쓰는 새 코드를 올리기 전에 먼저 실행하세요.
--
-- 컬럼은 추가(additive)라 기존 코드/쿼리에는 영향이 없습니다.

ALTER TABLE ADMIN_USER ADD (
    PW_VERSION NUMBER(10) DEFAULT 0 NOT NULL
);

COMMENT ON COLUMN ADMIN_USER.PW_VERSION IS '비밀번호 버전(변경 시 +1). 세션 자격증명 버전 비교로 비번 변경 시 기존 세션 무효화';
