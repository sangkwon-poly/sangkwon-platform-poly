# 여기콕 소상공인 상권분석 플랫폼

2026 웹 개발 프로젝트. Spring Boot 4.1 + Oracle Autonomous DB. 데이터분석과 4인 팀.

서울 상권 데이터를 지도에서 분석해 소상공인에게 창업 인사이트를 주는 웹 서비스.

## 핵심 기능

- **지도 상권분석**: 서울 1,650여 개 상권의 매출·유동인구·점포수·성장/쇠퇴를 지도에서 비교
- **AI 상권 리포트**: 선택한 상권 지표를 모아 Gemini로 근거 기반 요약 생성(무료 월 한도 / Pro 무제한)
- **업종·상권 동향**: 업종별 뉴스 인사이트, 상위 프랜차이즈 지표, 상표 출원 동향(공정위·KIPRIS 실연동)
- **창업 지원사업 매칭**: 기업마당·K-Startup 지원사업을 업종·지역으로 연결
- **Pro 구독 결제**: 토스페이먼츠 위젯 연동(월 24,000원 / 연 240,000원)
- **관리자 백오피스**: 회원·결제(대사·환불)·문의·공지·데이터 적재·API 사용량·감사 로그 13개 화면

설계(아키텍처·ERD·결제 상태 흐름)는 [`docs/설계.md`](docs/설계.md) 참고.

## 시연 안내

- 접속(예정): `https://sang.it.kr`
- 관리자 데모 계정: `admin` / `1234` (데모 전용)
- 추천 시연 순서:
  1. 랜딩 → 회원가입/로그인
  2. 지도에서 상권 클릭 → 매출·유동인구 지표 확인 → AI 상권 리포트 생성
  3. 업종·상권 동향(뉴스 인사이트·프랜차이즈·상표) 열람
  4. 요금제 → Pro 결제(토스 테스트 결제)
  5. 관리자 로그인 → 데이터 적재 카탈로그(신선도·API 사용량) → 결제/회원/문의 관리 → 감사 로그

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
src/main/resources/db/schema.sql   # 32개 테이블(도메인 30 + 세션 2), Oracle에 한 번 실행
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
