# 공공데이터 적재 스크립트

서울 상권분석 공공데이터를 DB에 적재하는 Python ETL. 상권 폴리곤·수백만 행 팩트는 앱 런타임에 부적합해서 적재는 파이썬으로, 앱은 조회/분석만 한다.

---

## 시나리오 1: 같은 노트북 (재실행 불필요)

로컬 XE 데이터는 노트북에 그대로 남는다. **이미 적재했으면 다시 안 돌려도 된다.** XE 서비스(`OracleServiceXE`)만 켜져 있으면 앱이 바로 붙는다.

---

## 시나리오 2: 새 PC / 빈 DB에 채우기

아래를 순서대로 하면 DB가 채워진다.

### 1. 라이브러리

```bash
pip install oracledb pyshp pyproj
```

### 2. DB 준비 (Oracle XE + SANG 스키마 + 테이블)

XE에 SYSDBA로 접속해 스키마를 만든다 (`sqlplus / as sysdba`):

```sql
alter session set container = XEPDB1;
create user SANG identified by "1234";          -- 이미 있으면 생략
grant connect, resource to SANG;
grant unlimited tablespace to SANG;             -- 대용량 적재라 필수
```

그리고 `src/main/resources/db/schema.sql`을 SANG 계정으로 실행해 25개 테이블을 만든다 (SQL Developer로 열어 실행하거나 `sqlplus SANG/1234@localhost:1521/XEPDB1 @schema.sql`).

### 3. API 키를 환경변수로 (application-local.properties의 값 사용)

PowerShell:

```powershell
$env:SEOUL_OPENDATA_KEY   = "..."   # 서울 열린데이터광장
$env:DATAGOKR_SERVICE_KEY = "..."   # data.go.kr (공정위 등)
$env:FTC_FRANCHISE_KEY    = "..."   # franchise.ftc.go.kr
$env:REB_RONE_KEY         = "..."   # 한국부동산원 R-ONE
```

### 4. 실행

```bash
python scripts/data-load/01_load_trdar_from_shapefile.py   # TRDAR 1,650 (폴리곤)
python scripts/data-load/02_seed_quarters_and_inspect.py   # DIM_QUARTER 48분기
python scripts/data-load/03_load_seoul_facts.py            # 서울 7개 팩트 ~220만행 + INDUTY (수십 분)
python scripts/data-load/04_load_franchise.py              # 공정위 프랜차이즈 3종
python scripts/data-load/05_load_commercial_rent.py        # 한국부동산원 임대동향 COMMERCIAL_RENT ~8만행
```

접속 정보가 다르면 각 스크립트 상단의 `USER/PW/DSN`만 바꾼다 (팀 공유 DB로 옮길 때 여기만 수정).

---

## 참고

- `03`은 시작할 때 **FK와 값-범위 CHECK 제약을 자동 비활성화**한다 (개업률 0~100 같은 제약을 실데이터가 안 지키는 경우가 있어서). 되돌리려면 `ALTER TABLE ... ENABLE CONSTRAINT`.
- `01`은 TRDAR의 `CK_TRDAR_GEO`(GEO_JSON IS JSON) 제약을 제거한다 (XE 21c에서 CLOB insert와 충돌).
- 좌표계: EPSG:5181(Korea 2000 중부원점) → EPSG:4326(WGS84).
- `05`는 R-ONE 통계표 목록에서 상업용부동산 임대동향(임대료·공실률·투자수익률·임대가격지수 × 오피스/중대형/소규모/집합상가)을 골라 적재한다. 임대가격지수는 시계열표, 나머지는 2019~2022~ 표를 쓰고 DIM_QUARTER(2015~) 범위만 남긴다. STATBL_ID는 표 이름으로 자동 선정한다.
- 창업지원(SUPPORT_PROGRAM)은 담당자가 별도 진행 (이슈 #10).
