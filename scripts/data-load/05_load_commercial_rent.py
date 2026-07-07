# -*- coding: utf-8 -*-
# 한국부동산원(R-ONE) 상업용부동산 임대동향조사 -> COMMERCIAL_RENT
# 지표: 임대료 / 공실률 / 투자수익률 / 임대가격지수, 유형: 오피스/중대형상가/소규모상가/집합상가
# 임대가격지수는 시계열표(2013~), 나머지는 2019~2022~ 표. DIM_QUARTER(2015~) 범위만 적재.
import os, sys, urllib.request, json, time, oracledb

KEY = os.environ["REB_RONE_KEY"]
BASE = "https://www.reb.or.kr/r-one/openapi"
DRY = "--dry" in sys.argv

def api(path, **p):
    q = "&".join(f"{k}={v}" for k, v in p.items())
    url = f"{BASE}/{path}?KEY={KEY}&Type=json&{q}"
    for a in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=50) as r:
                return json.loads(r.read().decode("utf-8", "replace"))
        except Exception:
            if a == 3:
                raise
            time.sleep(2)

TYPES = {"오피스": "오피스", "중대형 상가": "중대형상가", "소규모 상가": "소규모상가", "집합 상가": "집합상가"}
PERIODS = ("(2019년)", "(2020년)", "(2021년)", "(2022년~)")

def classify(nm):
    ty = next((v for k, v in TYPES.items() if nm.endswith("_" + k)), None)
    if not ty:
        return None
    if "지역별 임대료(" in nm and any(p in nm for p in PERIODS):
        return ("RENT", "임대료", ty)
    if "지역별 공실률(" in nm and any(p in nm for p in PERIODS):
        return ("VACANCY", "공실률", ty)
    if nm.startswith("임대동향 수익률(분기)(") and any(p in nm for p in PERIODS):
        return ("YIELD", "투자수익률", ty)
    if "지역별 임대가격지수(시계열)" in nm:
        return ("PRICE_INDEX", "임대가격지수", ty)
    return None

def list_targets():
    rows = []
    for p in range(1, 5):
        d = api("SttsApiTbl.do", pIndex=p, pSize=200)
        rr = [x for x in d["SttsApiTbl"] if "row" in x]
        if not rr:
            break
        rows += rr[0]["row"]
    out = []
    for r in rows:
        c = classify(r.get("STATBL_NM", ""))
        if c:
            out.append((r["STATBL_ID"], r["STATBL_NM"], *c))
    return out

def fetch_data(sid):
    got, page = [], 1
    while True:
        d = api("SttsApiTblData.do", STATBL_ID=sid, DTACYCLE_CD="QY", pIndex=page, pSize=1000)
        blk = d.get("SttsApiTblData", [])
        rr = [x for x in blk if "row" in x]
        if not rr:
            break
        chunk = rr[0]["row"]
        got += chunk
        if len(chunk) < 1000:
            break
        page += 1
    return got

def qcode(w):  # '202201' -> '20221'
    return f"{w[:4]}{int(w[4:6])}" if w and len(w) >= 6 else None

def main():
    targets = list_targets()
    print(f"대상 통계표 {len(targets)}개")
    by = {}
    for _, _, m, _, _ in targets:
        by[m] = by.get(m, 0) + 1
    print("  지표별:", by)
    if DRY:
        for sid, nm, mcd, mnm, ty in targets:
            print(f"  {sid:18} {mcd:12} {ty:8} {nm}")
        return

    con = oracledb.connect(user=os.environ.get("XE_DB_USER", "SANG"),
                           password=os.environ["XE_DB_PASSWORD"],  # 커밋 금지: set XE_DB_PASSWORD=1234
                           dsn="localhost:1521/XEPDB1")
    cur = con.cursor()
    quarters = {r[0] for r in cur.execute("SELECT STDR_YYQU_CD FROM DIM_QUARTER")}

    rows = {}  # (region_cd, rlst_ty, metric_cd, qcode) -> (region_nm, metric_nm, value, uom)
    for i, (sid, nm, mcd, mnm, ty) in enumerate(targets, 1):
        data = fetch_data(sid)
        kept = 0
        for r in data:
            qc = qcode(r.get("WRTTIME_IDTFR_ID"))
            if qc not in quarters:
                continue
            val = r.get("DTA_VAL")
            if val is None or val == "":
                continue
            reg = r.get("CLS_ID")
            if reg is None:
                continue
            reg = str(reg)
            regnm = (r.get("CLS_FULLNM") or r.get("CLS_NM") or reg)[:200]
            rows[(reg, ty, mcd, qc)] = (regnm, mnm, float(val), (r.get("UI_NM") or "")[:20])
            kept += 1
        print(f"[{i}/{len(targets)}] {mcd:12} {ty:6} {nm[:34]:34} 원본 {len(data):5} 채택 {kept}")

    cur.execute("DELETE FROM COMMERCIAL_RENT")
    con.commit()
    batch = [(reg, regnm, ty, mcd, mnm, qc, val, uom)
             for (reg, ty, mcd, qc), (regnm, mnm, val, uom) in rows.items()]
    cur.executemany(
        "INSERT INTO COMMERCIAL_RENT (REGION_CD,REGION_NM,RLST_TY_CD,METRIC_CD,METRIC_NM,STDR_YYQU_CD,METRIC_VALUE,UOM) "
        "VALUES (:1,:2,:3,:4,:5,:6,:7,:8)", batch)
    con.commit()
    print(f"COMMERCIAL_RENT 적재: {len(batch)}행")
    cur.execute("SELECT METRIC_CD, COUNT(*) FROM COMMERCIAL_RENT GROUP BY METRIC_CD ORDER BY METRIC_CD")
    for m, c in cur.fetchall():
        print(f"  {m:12} {c}")
    con.close()

main()
