# 정적 프론트엔드 규칙 (static)

서버 렌더링이 필요 없는 순수 정적 화면은 `static/` 아래에 둔다.
(서버 데이터를 HTML에 꽂아야 하는 화면이 생기면 그때만 `templates/` + 컨트롤러 + Thymeleaf 로 간다.)

## 폴더 규칙 · 백엔드 도메인 패키지를 그대로 미러링

HTML 은 도메인 폴더로, CSS/JS 는 `css/`·`js/` 아래 같은 도메인 폴더로 나눈다.

```
static/
├─ index.html              LANDING-01 (public 루트, GET /)
├─ FRONTEND.md             (이 문서)
├─ css/
│  ├─ common.css           공통 토큰 + 프리미티브 (.container/.btn/.brand/.badge)
│  ├─ landing.css
│  ├─ member/              회원 (member 패키지)
│  ├─ map/                 상권분석 조회 (map 패키지)
│  ├─ support/             창업지원 (support 패키지)
│  └─ admin/               백오피스 (admin 패키지)
├─ js/
│  └─ map/                 지도 스크립트 등 화면별 JS
├─ img/                    이미지 자산
├─ member/
│  └─ login.html           LOGIN-01
├─ map/
│  ├─ index.html           MAP-01  (GET /map/)
│  ├─ search.html          SEARCH-01
│  ├─ trdar-detail.html    TRDAR-01
│  ├─ compare.html         COMPARE-01
│  ├─ report.html          REPORT-01
│  ├─ insight.html         INSIGHT-01
│  └─ modals/              M-01~M-05 (오버레이 목업)
├─ support/
│  └─ support.html         SUPPORT-01
└─ admin/
   ├─ index.html           ADMIN-01 (GET /admin/)
   ├─ batch.html           BATCH-01
   ├─ trdar-admin.html     TRDAR-ADM-01
   └─ user-admin.html      USER-ADM-01
```

## 작성 규칙

- **시맨틱 HTML5**: `header/nav/main/section/article/figure/footer`, 제목은 `h1→h2→h3` 순서. 랜드마크에 `aria-label`.
- **CSS 로드 순서**: `common.css` 먼저, 그다음 화면별 CSS. 경로는 루트 절대경로(`/css/...`).
- **토큰 재사용**: 색·간격은 `common.css` 의 CSS 변수(`var(--brand)` 등)를 쓴다. 화면별 CSS 에서 `:root` 토큰을 재정의하지 않는다.
- **프리미티브 재사용**: 버튼은 `.btn .btn-primary`, 로고는 `.brand`, 폭은 `.container`, 상태 태그는 `.badge*` 를 재사용.
- **목업 프레임 제거**: 목업의 캡션 바(화면코드·WIP·`01/18`)와 고정폭 카드 액자는 제거하고 실제 페이지로 만든다.
- **반응형**: 데스크톱 기준, 태블릿(≤900px)·모바일(≤640px, 1열·햄버거) 대응.
- **주석**: 짧은 한국어만. 영어 서술 주석과 em-dash(—) 금지.
- **지도**: 실제 Kakao 연동은 이후 작업. 지금은 목업의 플레이스홀더 유지하되 컨테이너에 id 를 둔다.
```
