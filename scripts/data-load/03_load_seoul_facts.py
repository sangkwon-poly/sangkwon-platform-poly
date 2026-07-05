# -*- coding: utf-8 -*-
import os, oracledb, urllib.request, json, time, sys

KEY = os.environ["SEOUL_OPENDATA_KEY"]
LOG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "load_progress.log")

def log(msg):
    line = time.strftime("%H:%M:%S") + " " + msg
    with open(LOG, "a", encoding="utf-8") as f:
        f.write(line + "\n")
    print(line, flush=True)

# service -> (table, {api_field: table_col rename}, has_induty)
DATASETS = [
    ("VwsmTrdarSelngQq", "SALES",        {"SVC_INDUTY_CD": "INDUTY_CD"}, True),
    ("VwsmTrdarStorQq",  "STORE_STAT",   {"SVC_INDUTY_CD": "INDUTY_CD"}, True),
    ("VwsmTrdarIxQq",    "TRDAR_CHANGE", {}, False),
    ("VwsmTrdarFlpopQq", "STREET_POP",   {}, False),
    ("VwsmTrdarRepopQq", "RESIDENT_POP", {"APT_HSHLD_CO": "APT_HSHLD_RESIDENT_CO",
                                          "NON_APT_HSHLD_CO": "NON_APT_HSHLD_RESIDENT_CO"}, False),
    ("VwsmTrdarFcltyQq", "ATTRACTION",   {"BUS_STTN_CO": "BUS_STOP_CO"}, False),
    ("InfoTrdarAptQq",   "APT",          {"APT_HSMP_CO": "APT_COMPLX_CO",
                                          "AE_66_SQMT_HSHLD_CO": "AREA_66_HSHLD_CO",
                                          "AE_99_SQMT_HSHLD_CO": "AREA_99_HSHLD_CO",
                                          "AE_132_SQMT_HSHLD_CO": "AREA_132_HSHLD_CO",
                                          "AE_165_SQMT_HSHLD_CO": "AREA_165_HSHLD_CO",
                                          "PC_1_HDMIL_HSHLD_CO": "PRC_1_HSHLD_CO",
                                          "PC_2_HDMIL_HSHLD_CO": "PRC_2_HSHLD_CO",
                                          "PC_3_HDMIL_HSHLD_CO": "PRC_3_HSHLD_CO",
                                          "PC_4_HDMIL_HSHLD_CO": "PRC_4_HSHLD_CO",
                                          "PC_5_HDMIL_HSHLD_CO": "PRC_5_HSHLD_CO",
                                          "PC_6_HDMIL_ABOVE_HSHLD_CO": "PRC_6_HSHLD_CO",
                                          "AVRG_AE": "AVRG_AREA", "AVRG_MKTC": "AVRG_MRKT_PRC"}, False),
]

def fetch(svc, start, end):
    url = f"http://openapi.seoul.go.kr:8088/{KEY}/json/{svc}/{start}/{end}/"
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=40) as r:
                d = json.load(r)
            k = [x for x in d if x != 'RESULT'][0]
            return d[k]
        except Exception as e:
            if attempt == 3:
                raise
            time.sleep(2)

def num(v):
    if v is None or v == "":
        return None
    try:
        return float(v)
    except (ValueError, TypeError):
        return v

con = oracledb.connect(user="SANG", password="1234", dsn="localhost:1521/XEPDB1")
cur = con.cursor()

# FK 잠깐 끄기 (적재 순서/고아행 대비)
cur.execute("SELECT table_name, constraint_name FROM user_constraints WHERE constraint_type='R' AND status='ENABLED'")
fks = cur.fetchall()
for t, c in fks:
    cur.execute(f'ALTER TABLE "{t}" DISABLE CONSTRAINT "{c}"')
log(f"FK {len(fks)}개 비활성화")

induty = {}  # code -> name

for svc, tbl, rename, has_induty in DATASETS:
    cur.execute("SELECT column_name FROM user_tab_columns WHERE table_name=:1", [tbl])
    tcols = {r[0] for r in cur.fetchall()}
    first = fetch(svc, 1, 1)
    total = int(first['list_total_count'])
    api_fields = list(first['row'][0].keys())
    # (api_field, table_col) 목록
    pairs = [(f, f) for f in api_fields if f in tcols]
    for af, tc in rename.items():
        if af in api_fields and tc in tcols:
            pairs.append((af, tc))
    cols = [tc for _, tc in pairs]
    binds = ",".join(f":{i+1}" for i in range(len(cols)))
    sql = f'INSERT INTO {tbl} ({",".join(cols)}) VALUES ({binds})'
    log(f"[{tbl}] 시작: 총 {total}행, 컬럼 {len(cols)}개")
    cur.execute(f"DELETE FROM {tbl}")
    con.commit()

    loaded = 0
    start = 1
    PAGE = 1000
    batch = []
    while start <= total:
        blk = fetch(svc, start, start + PAGE - 1)
        rows = blk.get('row', [])
        if not rows:
            break
        for r in rows:
            if has_induty:
                code = r.get("SVC_INDUTY_CD")
                if code and code not in induty:
                    induty[code] = r.get("SVC_INDUTY_CD_NM")
            batch.append(tuple(num(r.get(af)) for af, _ in pairs))
        if len(batch) >= 5000:
            cur.executemany(sql, batch); con.commit(); loaded += len(batch); batch = []
            log(f"  [{tbl}] {loaded}/{total}")
        start += PAGE
    if batch:
        cur.executemany(sql, batch); con.commit(); loaded += len(batch)
    log(f"[{tbl}] 완료: {loaded}행")

# INDUTY 병합
if induty:
    cur.executemany(
        "MERGE INTO INDUTY d USING (SELECT :1 cd, :2 nm FROM dual) s ON (d.INDUTY_CD=s.cd) "
        "WHEN MATCHED THEN UPDATE SET INDUTY_CD_NM=s.nm "
        "WHEN NOT MATCHED THEN INSERT (INDUTY_CD, INDUTY_CD_NM) VALUES (s.cd, s.nm)",
        [(c, n) for c, n in induty.items()])
    con.commit()
    log(f"INDUTY 병합: {len(induty)}개 업종")

# 최종 건수
log("=== 최종 건수 ===")
for _, tbl, _, _ in DATASETS + [(None, "INDUTY", None, None), (None, "DIM_QUARTER", None, None), (None, "TRDAR", None, None)]:
    cur.execute(f"SELECT COUNT(*) FROM {tbl}")
    log(f"  {tbl}: {cur.fetchone()[0]}")
con.close()
log("전체 적재 완료")
