# -*- coding: utf-8 -*-
import os, oracledb, urllib.request, json

con = oracledb.connect(user="SANG", password="1234", dsn="localhost:1521/XEPDB1")
cur = con.cursor()

# 1) DIM_QUARTER 시드 (2015~2026, 48분기)
rows = []
for y in range(2015, 2027):
    for q in range(1, 5):
        rows.append((f"{y}{q}", y, q, f"{y}년 {q}분기"))
cur.execute("DELETE FROM DIM_QUARTER")
cur.executemany(
    "INSERT INTO DIM_QUARTER (STDR_YYQU_CD, BASE_YEAR, QUARTER_NO, QUARTER_LABEL) VALUES (:1,:2,:3,:4)",
    rows)
con.commit()
cur.execute("SELECT COUNT(*), MIN(STDR_YYQU_CD), MAX(STDR_YYQU_CD) FROM DIM_QUARTER")
c = cur.fetchone()

# 2) 7개 서울 팩트 서비스 필드 확인
KEY = os.environ["SEOUL_OPENDATA_KEY"]
services = ["VwsmTrdarSelngQq", "VwsmTrdarStorQq", "VwsmTrdarFcltyQq",
            "VwsmTrdarIxQq", "VwsmTrdarFlpopQq", "VwsmTrdarRepopQq", "InfoTrdarAptQq"]
out = [f"DIM_QUARTER 시드: {c[0]}개 ({c[1]}~{c[2]})", ""]
for svc in services:
    try:
        url = f"http://openapi.seoul.go.kr:8088/{KEY}/json/{svc}/1/1/"
        d = json.load(urllib.request.urlopen(url, timeout=25))
        k = [x for x in d if x != 'RESULT'][0]
        blk = d[k]
        row = blk['row'][0]
        out.append(f"### {svc}  (총 {blk['list_total_count']}행)")
        out.append("필드: " + ", ".join(row.keys()))
    except Exception as e:
        out.append(f"### {svc}  실패: {str(e)[:100]}")
open('seoul_fields.txt', 'w', encoding='utf-8').write("\n".join(out))
con.close()
print("done -> seoul_fields.txt")
