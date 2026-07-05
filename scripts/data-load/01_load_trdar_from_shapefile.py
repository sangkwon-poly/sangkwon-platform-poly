# -*- coding: utf-8 -*-
# OA-15560 상권영역 shapefile을 내려받아 WGS84로 변환 후 TRDAR에 적재.
# 필요한 것: pip install pyshp pyproj oracledb
import oracledb, urllib.request, zipfile, os, glob, json, io
import shapefile
from pyproj import Transformer

DSN = "localhost:1521/XEPDB1"
USER, PW = "SANG", "1234"          # 로컬 XE (팀 공유 DB로 옮기면 여기만 교체)
WORK = os.path.join(os.path.dirname(__file__), "_trdar_tmp")
os.makedirs(WORK, exist_ok=True)

# 1) 다운로드 (서울 열린데이터광장 파일 다운로드 엔드포인트)
zip_path = os.path.join(WORK, "trdar.zip")
if not os.path.exists(zip_path):
    req = urllib.request.Request(
        "https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do?&useCache=false",
        data=b"infId=OA-15560&seqNo=&seq=5&infSeq=3",
        headers={"User-Agent": "Mozilla/5.0", "Referer": "https://data.seoul.go.kr/"})
    with urllib.request.urlopen(req, timeout=120) as r, open(zip_path, "wb") as f:
        f.write(r.read())
with zipfile.ZipFile(zip_path) as z:
    z.extractall(WORK)

# 2) shapefile 읽기 (.dbf 인코딩 utf-8), EPSG:5181 -> WGS84
shp = glob.glob(os.path.join(WORK, "**", "*.shp"), recursive=True)[0]
r = shapefile.Reader(shp, encoding="utf-8")
fields = [f[0] for f in r.fields[1:]]
idx = {n: i for i, n in enumerate(fields)}
t = Transformer.from_crs(5181, 4326, always_xy=True)

def reproj(coords):
    if coords and isinstance(coords[0], (int, float)):
        lon, lat = t.transform(coords[0], coords[1])
        return [round(lon, 6), round(lat, 6)]
    return [reproj(c) for c in coords]

rows = []
for i in range(len(r)):
    rec, sh = r.record(i), r.shape(i)
    geo = sh.__geo_interface__
    gj = json.dumps({"type": geo["type"], "coordinates": reproj(geo["coordinates"])},
                    ensure_ascii=False, separators=(",", ":"))
    clon, clat = t.transform(rec[idx["XCNTS_VALU"]], rec[idx["YDNTS_VALU"]])
    rows.append((str(rec[idx["TRDAR_CD"]]), rec[idx["TRDAR_CD_N"]], rec[idx["TRDAR_SE_C"]],
                 rec[idx["TRDAR_SE_1"]], str(rec[idx["SIGNGU_CD"]]), rec[idx["SIGNGU_CD_"]],
                 str(rec[idx["ADSTRD_CD"]]), rec[idx["ADSTRD_CD_"]],
                 round(clon, 8), round(clat, 8), gj))

# 3) 적재 (GEO_JSON은 CLOB -> createClob 바인드. IS JSON 체크는 XE 21c에서 CLOB insert와 충돌해 제거)
con = oracledb.connect(user=USER, password=PW, dsn=DSN)
cur = con.cursor()
try:
    cur.execute("ALTER TABLE TRDAR DROP CONSTRAINT CK_TRDAR_GEO")
except oracledb.DatabaseError:
    pass
cur.execute("DELETE FROM TRDAR")
sql = ("INSERT INTO TRDAR (TRDAR_CD,TRDAR_CD_NM,TRDAR_SE_CD,TRDAR_SE_CD_NM,SIGNGU_CD,SIGNGU_NM,"
       "ADSTRD_CD,ADSTRD_NM,CENTER_LOT,CENTER_LAT,GEO_JSON) VALUES (:1,:2,:3,:4,:5,:6,:7,:8,:9,:10,:11)")
for row in rows:
    clob = con.createClob(); clob.write(row[10])
    cur.execute(sql, row[:10] + (clob,))
    clob.close()
con.commit()
cur.execute("SELECT COUNT(*) FROM TRDAR")
print("TRDAR 적재:", cur.fetchone()[0])
con.close()
