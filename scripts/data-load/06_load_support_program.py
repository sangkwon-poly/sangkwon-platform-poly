"""
지원사업 데이터 적재 스크립트 (SUPPORT_PROGRAM)
- Oracle Autonomous DB (지갑, Thick 모드) 연결
- 날짜(APPLY_BGNG_DE, APPLY_END_DE)는 SQL 문자열에 TO_DATE(...) 리터럴로 직접 삽입
- SOURCE_REG_DT(TIMESTAMP)는 파이썬 datetime 객체로 변환해서 바인딩
- CREATED_AT / UPDATED_AT은 SYSTIMESTAMP(UTC) 대신 파이썬에서 한국시간(KST)으로 생성해서 바인딩
- 기업마당(BIZINFO) + K-Startup(KSTARTUP) API 호출 (서울 지역 필터, 전체 페이지, 재시도 포함)
- SUPPORT_PROGRAM / SUPPORT_PROGRAM_KSTARTUP_DETAIL 테이블에 저장
"""

import os
import re
import html
import time
from datetime import datetime, timezone, timedelta

import requests
import oracledb
from dotenv import load_dotenv

load_dotenv("properties.env")

DB_USERNAME = os.environ["DB_USERNAME"]
DB_PASSWORD = os.environ["DB_PASSWORD"]
DB_TNS_ALIAS = os.environ["DB_TNS_ALIAS"]
DB_WALLET_DIR = os.environ["DB_WALLET_DIR"]

KSTARTUP_SERVICE_KEY = os.environ["KSTARTUP_SERVICE_KEY"]
BIZINFO_SERVICE_KEY = os.environ["BIZINFO_SERVICE_KEY"]

# Thick 모드 활성화 (Instant Client 경로는 본인 환경에 맞게 수정)
oracledb.init_oracle_client(
    lib_dir=r"C:\Users\USER\Downloads\instantclient-basic-windows.x64-23.26.2.0.0\instantclient_23_0"
)
# Thick 모드는 config_dir이 아니라 TNS_ADMIN 환경변수로 지갑 위치를 찾음
os.environ["TNS_ADMIN"] = DB_WALLET_DIR

# 한국시간(KST) 정의 - DB 서버(UTC) 시간대와 무관하게 항상 한국시간으로 저장
KST = timezone(timedelta(hours=9))


def now_kst():
    return datetime.now(KST)


def get_connection():
    return oracledb.connect(
        user=DB_USERNAME,
        password=DB_PASSWORD,
        dsn=DB_TNS_ALIAS,
        wallet_location=DB_WALLET_DIR,
    )


# ============================================================
# 공통 유틸 함수
# ============================================================
def strip_html(text):
    if not text:
        return None
    return re.sub(r"<[^>]*>", "", text).strip()


def parse_period(raw):
    """기업마당 실제 응답: '2026-07-01 ~ 2026-07-31' (하이픈 포함) -> 'YYYYMMDD' 문자열"""
    if not raw or "~" not in raw:
        return None, None
    try:
        start_str, end_str = [p.strip() for p in raw.split("~")]
        start = datetime.strptime(start_str, "%Y-%m-%d").strftime("%Y%m%d")
        end = datetime.strptime(end_str, "%Y-%m-%d").strftime("%Y%m%d")
        return start, end
    except ValueError:
        return None, None


def parse_kstartup_date(value):
    """K-Startup 실제 응답: '20260703' (하이픈 없는 YYYYMMDD) -> 'YYYYMMDD' 문자열"""
    if not value:
        return None
    try:
        return datetime.strptime(str(value), "%Y%m%d").strftime("%Y%m%d")
    except ValueError:
        return None


def parse_source_reg_dt(value):
    """기업마당 creatPnttm: '2022-09-02 15:38:29' -> datetime 객체 (TIMESTAMP 컬럼용)"""
    if not value:
        return None
    try:
        return datetime.strptime(value, "%Y-%m-%d %H:%M:%S")
    except ValueError:
        return None


def date_literal(yyyymmdd):
    """'20260703' -> "TO_DATE('20260703','YYYYMMDD')" / None -> "NULL" (SQL에 직접 박아넣을 리터럴)"""
    if not yyyymmdd:
        return "NULL"
    if not re.fullmatch(r"\d{8}", yyyymmdd):
        return "NULL"
    return f"TO_DATE('{yyyymmdd}', 'YYYYMMDD')"


def fetch_with_retry(url, params, max_retries=3, timeout=30):
    """타임아웃/에러 발생 시 자동 재시도"""
    for attempt in range(1, max_retries + 1):
        try:
            resp = requests.get(url, params=params, timeout=timeout)
            resp.raise_for_status()
            return resp.json()
        except requests.exceptions.RequestException as e:
            print(f"  요청 실패 ({attempt}/{max_retries}회): {e}")
            if attempt == max_retries:
                raise
            time.sleep(3)


# ============================================================
# 1) 기업마당 API 호출 (전체 페이지, 서울 지역만)
# ============================================================
def fetch_bizinfo():
    url = "https://www.bizinfo.go.kr/uss/rss/bizinfoApi.do"
    rows = []
    page_index = 1

    while True:
        params = {
            "crtfcKey": BIZINFO_SERVICE_KEY,
            "dataType": "json",
            "searchLclasId": "06",
            "hashtags": "서울",
            "pageUnit": "500",
            "pageIndex": str(page_index),
        }
        data = fetch_with_retry(url, params)

        items = data.get("jsonArray", [])
        if not items:
            break

        for item in items:
            start_de, end_de = parse_period(item.get("reqstBeginEndDe"))
            rows.append({
                "program_id": str(item.get("pblancId")),
                "source_cd": "BIZINFO",
                "title": item.get("pblancNm"),
                "program_type": item.get("pldirSportRealmLclasCodeNm"),
                "target": item.get("trgetNm"),
                "region": None,
                "description": strip_html(item.get("bsnsSumryCn")),
                "apply_bgng_de": start_de,
                "apply_end_de": end_de,
                "apply_period_raw": item.get("reqstBeginEndDe"),
                "recruit_yn": None,
                "contact": None,
                "detail_url": item.get("pblancUrl"),
                "source_reg_dt": parse_source_reg_dt(item.get("creatPnttm")),
            })

        print(f"  기업마당 {page_index}페이지 {len(items)}건 수집")

        if len(items) < 500:
            break
        page_index += 1

    return rows


# ============================================================
# 2) K-Startup API 호출 (전체 페이지, 서울 지역만)
# ============================================================
def fetch_kstartup():
    url = "https://apis.data.go.kr/B552735/kisedKstartupService01/getAnnouncementInformation01"
    rows = []
    detail_rows = []
    page = 1

    while True:
        params = {
            "ServiceKey": KSTARTUP_SERVICE_KEY,
            "page": str(page),
            "perPage": "500",
            "returnType": "json",
            "supt_regin": "서울특별시",
        }
        data = fetch_with_retry(url, params)

        items = data.get("data") or data.get("items") or []
        if not items:
            break

        for item in items:
            program_id = str(item.get("pbanc_sn"))

            rows.append({
                "program_id": program_id,
                "source_cd": "KSTARTUP",
                "title": item.get("biz_pbanc_nm"),
                "program_type": item.get("supt_biz_clsfc"),
                "target": " / ".join(filter(None, [
                    item.get("aply_trgt"),
                    item.get("biz_enyy"),
                    item.get("biz_trgt_age"),
                ])),
                "region": item.get("supt_regin"),
                "description": item.get("pbanc_ctnt"),
                "apply_bgng_de": parse_kstartup_date(item.get("pbanc_rcpt_bgng_dt")),
                "apply_end_de": parse_kstartup_date(item.get("pbanc_rcpt_end_dt")),
                "apply_period_raw": None,
                "recruit_yn": item.get("rcrt_prgs_yn"),
                "contact": item.get("prch_cnpl_no"),
                "detail_url": item.get("detl_pg_url"),
                "source_reg_dt": None,
            })

            detail_rows.append({
                "program_id": program_id,
                "source_cd": "KSTARTUP",
                "aply_mthd_vst": item.get("aply_mthd_vst_rcpt_istc"),
                "aply_mthd_pssr": item.get("aply_mthd_pssr_rcpt_istc"),
                "aply_mthd_fax": item.get("aply_mthd_fax_rcpt_istc"),
                "aply_mthd_eml": item.get("aply_mthd_eml_rcpt_istc"),
                "aply_mthd_onli": item.get("aply_mthd_onli_rcpt_istc"),
                "aply_mthd_etc": item.get("aply_mthd_etc_istc"),
                "aply_excl_trgt_ctnt": item.get("aply_excl_trgt_ctnt"),
                "biz_enyy": item.get("biz_enyy"),
                "biz_trgt_age": item.get("biz_trgt_age"),
                "prfn_matr": item.get("prfn_matr"),
                "sprv_inst": item.get("sprv_inst"),
                "pbanc_ntrp_nm": item.get("pbanc_ntrp_nm"),
                "biz_gdnc_url": item.get("biz_gdnc_url"),
                "intg_pbanc_yn": item.get("intg_pbanc_yn"),
            })

        print(f"  K-Startup {page}페이지 {len(items)}건 수집")

        if len(items) < 500:
            break
        page += 1

    return rows, detail_rows


# ============================================================
# 3) DB 저장 - SUPPORT_PROGRAM
#    - APPLY_BGNG_DE / APPLY_END_DE: SQL 문자열에 TO_DATE(...) 리터럴로 직접 삽입
#    - SOURCE_REG_DT: 파이썬 datetime 객체로 바인딩 (TIMESTAMP 컬럼)
#    - CREATED_AT / UPDATED_AT: SYSTIMESTAMP 대신 파이썬에서 만든 KST 시각을 바인딩
# ============================================================
def build_merge_program_sql(bgng_literal, end_literal):
    return f"""
    MERGE INTO SUPPORT_PROGRAM tgt
    USING (SELECT :program_id AS PROGRAM_ID, :source_cd AS SOURCE_CD FROM dual) src
    ON (tgt.PROGRAM_ID = src.PROGRAM_ID AND tgt.SOURCE_CD = src.SOURCE_CD)
    WHEN MATCHED THEN UPDATE SET
        TITLE = :title,
        PROGRAM_TYPE = :program_type,
        TARGET = :target,
        REGION = :region,
        DESCRIPTION = :description,
        APPLY_BGNG_DE = {bgng_literal},
        APPLY_END_DE = {end_literal},
        APPLY_PERIOD_RAW = :apply_period_raw,
        RECRUIT_YN = :recruit_yn,
        CONTACT = :contact,
        DETAIL_URL = :detail_url,
        SOURCE_REG_DT = :source_reg_dt,
        UPDATED_AT = :updated_at
    WHEN NOT MATCHED THEN INSERT (
        PROGRAM_ID, SOURCE_CD, TITLE, PROGRAM_TYPE, TARGET, REGION, DESCRIPTION,
        APPLY_BGNG_DE, APPLY_END_DE, APPLY_PERIOD_RAW, RECRUIT_YN,
        CONTACT, DETAIL_URL, SOURCE_REG_DT, CREATED_AT, UPDATED_AT
    ) VALUES (
        :program_id, :source_cd, :title, :program_type, :target, :region, :description,
        {bgng_literal}, {end_literal},
        :apply_period_raw, :recruit_yn,
        :contact, :detail_url, :source_reg_dt, :created_at, :updated_at
    )
    """


MERGE_DETAIL_SQL = """
MERGE INTO SUPPORT_PROGRAM_KSTARTUP_DETAIL tgt
USING (SELECT :program_id AS PROGRAM_ID, :source_cd AS SOURCE_CD FROM dual) src
ON (tgt.PROGRAM_ID = src.PROGRAM_ID AND tgt.SOURCE_CD = src.SOURCE_CD)
WHEN MATCHED THEN UPDATE SET
    APLY_MTHD_VST = :aply_mthd_vst,
    APLY_MTHD_PSSR = :aply_mthd_pssr,
    APLY_MTHD_FAX = :aply_mthd_fax,
    APLY_MTHD_EML = :aply_mthd_eml,
    APLY_MTHD_ONLI = :aply_mthd_onli,
    APLY_MTHD_ETC = :aply_mthd_etc,
    APLY_EXCL_TRGT_CTNT = :aply_excl_trgt_ctnt,
    BIZ_ENYY = :biz_enyy,
    BIZ_TRGT_AGE = :biz_trgt_age,
    PRFN_MATR = :prfn_matr,
    SPRV_INST = :sprv_inst,
    PBANC_NTRP_NM = :pbanc_ntrp_nm,
    BIZ_GDNC_URL = :biz_gdnc_url,
    INTG_PBANC_YN = :intg_pbanc_yn,
    UPDATED_AT = :updated_at
WHEN NOT MATCHED THEN INSERT (
    PROGRAM_ID, SOURCE_CD, APLY_MTHD_VST, APLY_MTHD_PSSR, APLY_MTHD_FAX,
    APLY_MTHD_EML, APLY_MTHD_ONLI, APLY_MTHD_ETC, APLY_EXCL_TRGT_CTNT,
    BIZ_ENYY, BIZ_TRGT_AGE, PRFN_MATR, SPRV_INST, PBANC_NTRP_NM,
    BIZ_GDNC_URL, INTG_PBANC_YN, CREATED_AT, UPDATED_AT
) VALUES (
    :program_id, :source_cd, :aply_mthd_vst, :aply_mthd_pssr, :aply_mthd_fax,
    :aply_mthd_eml, :aply_mthd_onli, :aply_mthd_etc, :aply_excl_trgt_ctnt,
    :biz_enyy, :biz_trgt_age, :prfn_matr, :sprv_inst, :pbanc_ntrp_nm,
    :biz_gdnc_url, :intg_pbanc_yn, :created_at, :updated_at
)
"""


def save_programs(rows):
    if not rows:
        print("SUPPORT_PROGRAM: 저장할 데이터 없음")
        return
    conn = get_connection()
    cursor = conn.cursor()
    ok = 0
    failed = []
    now = now_kst()
    for row in rows:
        bgng_literal = date_literal(row["apply_bgng_de"])
        end_literal = date_literal(row["apply_end_de"])
        sql = build_merge_program_sql(bgng_literal, end_literal)

        # 날짜 두 개는 SQL에 이미 리터럴로 박혀있으니 바인드에서 제외
        bind = {k: v for k, v in row.items() if k not in ("apply_bgng_de", "apply_end_de")}
        bind["created_at"] = now
        bind["updated_at"] = now

        try:
            cursor.execute(sql, bind)
            ok += 1
        except oracledb.DatabaseError as e:
            failed.append((row, str(e)))

    conn.commit()
    cursor.close()
    conn.close()

    print(f"SUPPORT_PROGRAM: 성공 {ok}건 / 실패 {len(failed)}건")
    if failed:
        print("\n=== 실패한 행 (최대 10건) ===")
        for row, err in failed[:10]:
            print(f"- program_id={row['program_id']} source_cd={row['source_cd']}")
            print(f"  apply_bgng_de={row['apply_bgng_de']!r} apply_end_de={row['apply_end_de']!r}")
            print(f"  source_reg_dt={row['source_reg_dt']!r}")
            print(f"  에러: {err}\n")


def save_details(rows):
    if not rows:
        print("SUPPORT_PROGRAM_KSTARTUP_DETAIL: 저장할 데이터 없음")
        return
    conn = get_connection()
    now = now_kst()
    for row in rows:
        row["created_at"] = now
        row["updated_at"] = now
    try:
        cursor = conn.cursor()
        cursor.executemany(MERGE_DETAIL_SQL, rows)
        conn.commit()
        print(f"SUPPORT_PROGRAM_KSTARTUP_DETAIL: {len(rows)}건 저장 완료")
    finally:
        cursor.close()
        conn.close()


# ============================================================
# 실행부
# ============================================================
if __name__ == "__main__":
    print("=== 기업마당 수집 시작 ===")
    bizinfo_rows = fetch_bizinfo()
    print(f"기업마당 {len(bizinfo_rows)}건 수집됨")

    print("=== K-Startup 수집 시작 ===")
    kstartup_rows, kstartup_detail_rows = fetch_kstartup()
    print(f"K-Startup {len(kstartup_rows)}건 수집됨")

    print("=== DB 저장 시작 ===")
    save_programs(bizinfo_rows + kstartup_rows)
    save_details(kstartup_detail_rows)

    print("=== 완료 ===")