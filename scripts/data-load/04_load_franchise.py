# -*- coding: utf-8 -*-
import os, oracledb, urllib.request, urllib.parse, json, time, datetime
import xml.etree.ElementTree as ET

DATAGOKR = os.environ["DATAGOKR_SERVICE_KEY"]
FTC = os.environ["FTC_FRANCHISE_KEY"]

def http(url, headers=None):
    req = urllib.request.Request(url, headers=headers or {})
    for a in range(4):
        try:
            with urllib.request.urlopen(req, timeout=40) as r:
                return r.read().decode("utf-8", "replace")
        except Exception:
            if a == 3: raise
            time.sleep(2)

def items(js):
    d = json.loads(js)
    stack = [d]
    while stack:
        x = stack.pop()
        if isinstance(x, list) and x and isinstance(x[0], dict):
            return x
        if isinstance(x, dict):
            stack.extend(x.values())
    return []

def total(js):
    d = json.loads(js)
    stack = [d]
    while stack:
        x = stack.pop()
        if isinstance(x, dict):
            if 'totalCount' in x:
                try: return int(x['totalCount'])
                except: pass
            stack.extend(x.values())
    return 0

def to_date(v):
    if not v: return None
    s = "".join(ch for ch in str(v) if ch.isdigit())
    if len(s) == 8:
        try: return datetime.date(int(s[:4]), int(s[4:6]), int(s[6:8]))
        except: return None
    return None

def numf(v):
    try: return float(v)
    except (ValueError, TypeError): return None

con = oracledb.connect(user=os.environ.get("XE_DB_USER", "SANG"),
                       password=os.environ["XE_DB_PASSWORD"],  # 커밋 금지: set XE_DB_PASSWORD=1234
                       dsn="localhost:1521/XEPDB1")
cur = con.cursor()

# 1) 브랜드목록 (2023 스냅샷) -> FRANCHISE_BRAND
cur.execute("DELETE FROM FRANCHISE_BRAND")
url0 = f"https://apis.data.go.kr/1130000/FftcBrandRlsInfo2_Service/getBrandinfo?serviceKey={urllib.parse.quote(DATAGOKR)}&pageNo=1&numOfRows=1&resultType=json&jngBizCrtraYr=2023"
tot = total(http(url0))
page, PER, seen = 1, 500, set()
brows = []
while (page - 1) * PER < tot:
    js = http(f"https://apis.data.go.kr/1130000/FftcBrandRlsInfo2_Service/getBrandinfo?serviceKey={urllib.parse.quote(DATAGOKR)}&pageNo={page}&numOfRows={PER}&resultType=json&jngBizCrtraYr=2023")
    its = items(js)
    if not its: break
    for it in its:
        mno = it.get("brandMnno")
        if not mno or mno in seen: continue
        seen.add(mno)
        brows.append((mno, it.get("brandNm"), it.get("corpNm"), it.get("jnghdqrtrsMnno"),
                      it.get("brno"), it.get("indutyLclasNm"), it.get("indutyMlsfcNm"), to_date(it.get("jngBizStrtDate"))))
    page += 1
cur.executemany("INSERT INTO FRANCHISE_BRAND (BRAND_MGMT_NO,BRAND_NM,CORP_NM,HQ_MGMT_NO,BIZ_REG_NO,INDUTY_LCLAS_NM,INDUTY_MLSFC_NM,BIZ_START_DE) VALUES (:1,:2,:3,:4,:5,:6,:7,:8)", brows)
con.commit()
print(f"FRANCHISE_BRAND: {len(brows)}")

# 2) 가맹점수 (2019~2023) -> FRANCHISE_COUNT  (AREA_CD=areaNm 대체)
cur.execute("DELETE FROM FRANCHISE_COUNT")
crows = []
for yr in range(2019, 2024):
    u0 = f"https://apis.data.go.kr/1130000/FftcindutyfrcscntstatService/getindutyfrcscntstats?serviceKey={urllib.parse.quote(DATAGOKR)}&pageNo=1&numOfRows=1&resultType=json&jngBizCrtraYr={yr}"
    tot = total(http(u0)); page = 1
    while (page - 1) * PER < tot:
        js = http(f"https://apis.data.go.kr/1130000/FftcindutyfrcscntstatService/getindutyfrcscntstats?serviceKey={urllib.parse.quote(DATAGOKR)}&pageNo={page}&numOfRows={PER}&resultType=json&jngBizCrtraYr={yr}")
        its = items(js)
        if not its: break
        for it in its:
            area = it.get("areaNm") or "전국"
            induty = it.get("indutyMlsfcNm") or it.get("indutyLclasNm") or "기타"
            crows.append((int(it.get("jngBizCrtraYr") or yr), area[:20], area, induty, numf(it.get("frcsCnt")) or 0, numf(it.get("frcsRate"))))
        page += 1
cur.executemany("INSERT INTO FRANCHISE_COUNT (BASE_YEAR,AREA_CD,AREA_NM,INDUTY_NM,FRC_CO,FRC_RT) VALUES (:1,:2,:3,:4,:5,:6)", crows)
con.commit()
print(f"FRANCHISE_COUNT: {len(crows)}")

# 3) 정보공개서 (2019~2023, XML) -> FRANCHISE_DISCLOSURE
cur.execute("DELETE FROM FRANCHISE_DISCLOSURE")
drows, dseen = [], set()
for yr in range(2019, 2024):
    xml = http(f"https://franchise.ftc.go.kr/api/search.do?type=list&yr={yr}&serviceKey={urllib.parse.quote(FTC)}", {"User-Agent": "Mozilla/5.0"})
    try:
        root = ET.fromstring(xml)
    except Exception:
        continue
    for it in root.iter("item"):
        sn = (it.findtext("jngIfrmpSn") or "").strip()
        if not sn or sn in dseen: continue
        dseen.add(sn)
        drows.append((sn, it.findtext("corpNm"), it.findtext("brandNm"), it.findtext("brno"), it.findtext("viwerUrl")))
cur.executemany("INSERT INTO FRANCHISE_DISCLOSURE (DISCLOSURE_SN,CORP_NM,BRAND_NM,BIZ_REG_NO,VIEWER_URL) VALUES (:1,:2,:3,:4,:5)", drows)
con.commit()
print(f"FRANCHISE_DISCLOSURE: {len(drows)}")
con.close()
print("프랜차이즈 3종 완료")
