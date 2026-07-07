# 서울공화국 소상공인 상권분석 플랫폼

2026 웹 개발 프로젝트. Spring Boot 4.1 + Oracle Autonomous DB. 데이터분석과 4인 팀.

서울 상권 데이터를 지도에서 분석해 소상공인에게 창업 인사이트를 주는 웹 서비스.

## 실행 전 세팅 (팀원 각자 한 번)

이 세팅을 안 하면 실행되지 않습니다. 키는 `.gitignore` 처리돼 저장소에 없습니다.

1. 로컬 설정 파일 만들기

   ```bash
   cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
   ```

   복사한 파일을 열어 DB 계정과 API 키를 채웁니다. 값은 조장에게 받습니다.

2. DB 지갑

   Oracle Cloud 지갑(zip)을 각자 PC에 압축 해제하고, 그 경로를 `DB_WALLET_DIR`에 넣습니다.

## 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

IntelliJ는 Run Configuration의 Active profiles에 `local`을 넣으면 됩니다.

## DB 스키마

```
src/main/resources/db/schema.sql   # 25개 테이블, Oracle에 한 번 실행
```

## 패키지 구조와 담당

```
com.sangkwon.sangkwonplatform
├─ global/    공통 (예외처리, 응답포맷, BaseEntity, 외부API)   조장
├─ map/       상권/지도            이상혁
├─ member/    회원/인증            김민혁
├─ admin/     관리자 백오피스       곽소정
└─ support/   창업지원/업계동향     양은영
```

각자 자기 도메인 패키지만 수정합니다. `global`은 바꿀 때 팀에 공유합니다.

## 공통 규약

- 응답은 `ApiResponse`로 감쌉니다. 예외는 `throw new BusinessException(ErrorCode.XXX)`.
- DTO는 요청 `{도메인}{동작}Request`, 응답 `{도메인}Response`. 엔티티 직접 반환은 금지.
- 에러코드는 `ErrorCode` enum에 도메인 접두사(M/D/A/S)로 추가합니다.
