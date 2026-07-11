# 정적 프론트엔드 규칙 (static)

서버 렌더링이 필요 없는 순수 정적 화면은 `static/` 아래에 둔다.
(서버 데이터를 HTML에 꽂아야 하는 화면이 생기면 그때만 `templates/` + 컨트롤러 + Thymeleaf 로 간다.)

## 폴더 규칙 · 화면 폴더가 자기 자산을 소유한다

화면(도메인) 폴더가 자신의 HTML/CSS/JS/이미지를 전부 가진다.
둘 이상의 화면이 같이 쓰는 자산만 `common/` 에 둔다.

```
static/
├─ index.html              랜딩 (public 루트, GET /)
├─ FRONTEND.md             (이 문서)
├─ common/                 여러 화면이 공유하는 자산만
│  ├─ css/common.css       공통 토큰 + 프리미티브 (.container/.btn/.brand/.badge)
│  └─ js/induty-map.js     업종 마스터 (map + industry 공용)
├─ landing/                index.html 전용 자산 (css/js/images)
├─ favicon/                파비콘
├─ geo/                    지도 데이터 (seoul-gu.json)
├─ map/                    상권분석: html + css/ + js/ + modals/
├─ member/                 회원: html + css/ + js/
├─ industry/               업종·상권 동향: html + css/ + js/
├─ support/                지원사업: html + css/ + js/
├─ pricing/                요금제: html + css/ + js/
├─ notice/                 공지: html + css/ + js/
├─ inquiry/                1:1 문의: html + css/ + js/
└─ admin/                  백오피스: html + css/ + js/
```

- 새 화면을 만들 때는 화면 폴더 안에 `css/`, `js/` 를 만들고 그 안에만 둔다.
- 자산이 두 번째 화면에서 필요해지는 순간 `common/` 으로 옮기고 두 화면 모두 절대경로로 참조한다.

### 예외: `member/`

회원 영역(`member/`)은 자체 팔레트(`member/css/member.css` 가 `:root` 토큰을 독립 정의)를 쓰는 독립 화면이라 `common.css` 를 로드하지 않고 아래 절대경로 규칙도 따르지 않는다. 자산을 **상대경로**(`css/member.css`, `js/api.js`)로 참조하는데, 이는 의도된 것이다. 랜딩 단축 경로(`/login`, `/member`)가 forward 가 아니라 **redirect** 로 걸려 있어(`PageRoutesConfig`) 브라우저 주소가 항상 `/member/...` 로 확정되기 때문이다. member 화면을 손볼 때 이 상대경로를 절대경로로 바꾸거나 forward 로 전환하지 말 것.

## 작성 규칙

- **시맨틱 HTML5**: `header/nav/main/section/article/figure/footer`, 제목은 `h1→h2→h3` 순서. 랜드마크에 `aria-label`.
- **CSS 로드 순서**: `common.css` 먼저, 그다음 화면별 CSS. 경로는 루트 절대경로(`/common/css/common.css`, `/map/css/...`).
- **토큰 재사용**: 색·간격은 `common.css` 의 CSS 변수(`var(--brand)` 등)를 쓴다. 화면별 CSS 에서 `:root` 토큰을 재정의하지 않는다.
- **프리미티브 재사용**: 버튼은 `.btn .btn-primary`, 로고는 `.brand`, 폭은 `.container`, 상태 태그는 `.badge*` 를 재사용.
- **반응형**: 데스크톱 기준, 태블릿(≤900px)·모바일(≤640px, 1열·햄버거) 대응.
- **주석**: 짧은 한국어만. 영어 서술 주석과 em-dash(—) 금지.
