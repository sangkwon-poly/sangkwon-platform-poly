# 공공데이터 적재 스크립트

서울 상권분석 공공데이터를 DB에 적재하는 Python 스크립트 모음.

## 왜 Python인가

상권 폴리곤(shapefile)·수백만 행 팩트는 Spring 앱 런타임에서 다루기 부적합해서, 적재는 파이썬 ETL로 하고 앱은 적재된 데이터를 조회/분석만 한다.

## DB

현재 로컬 Oracle XE(`SANG` 스키마)에 적재한다. 학교 클라우드 DB는 스키마 할당량이 30MB라 대용량이 안 들어간다. 나중에 할당량을 늘리거나 팀 공유 DB가 생기면 각 스크립트 상단의 접속 정보(`DSN`, `USER`, `PW`)만 바꿔 그대로 다시 돌리면 된다.

## 준비

```bash
pip install oracledb pyshp pyproj
# 로컬 XE에 SANG 스키마 + schema.sql 25개 테이블이 있어야 함
```

## 실행 순서

| 순서 | 스크립트 | 내용 |
|---|---|---|
| 1 | `01_load_trdar_from_shapefile.py` | OA-15560 상권영역 shapefile 다운로드 → WGS84 변환 → TRDAR(1,650, 폴리곤) |
| 2 | `02_seed_quarters_and_inspect.py` | DIM_QUARTER 분기 시드 + 서울 API 필드 확인 |
| 3 | `03_load_seoul_facts.py` | 서울 7개 팩트(매출·점포·변화·유동·상주·집객·아파트) 전량 + INDUTY 수집·병합 |
| 4 | `04_load_franchise.py` | 공정위 브랜드·가맹점수·정보공개서 |

## 참고

- `03`은 약 220만 행이라 수십 분 걸린다. 실행 전 팩트 테이블의 값-범위 CHECK 제약(개업률 0~100 등)을 비활성화해야 한다. 실제 공공데이터가 이상적 범위를 안 지키는 경우가 있기 때문.
- TRDAR의 `CK_TRDAR_GEO`(GEO_JSON IS JSON) 제약은 XE 21c에서 CLOB insert와 충돌해 `01`이 제거한다.
- 좌표계는 EPSG:5181(Korea 2000 중부원점) → EPSG:4326(WGS84).
