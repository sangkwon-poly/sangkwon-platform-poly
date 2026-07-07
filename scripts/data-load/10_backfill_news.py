"""
업종 뉴스 - 1회성 소급 적재(backfill) 스크립트
- daily/monthly처럼 주기적으로 도는 코드가 아니라, "지금 한 번만" 실행해서
  네이버 뉴스 API가 허용하는 최대치(start 최대 1000, 즉 최신순 최대 1000건)까지
  업종별로 전부 긁어와 INDUSTRY_NEWS에 채워넣는다.

주의:
- 네이버 뉴스 API는 한 검색어당 start=1~1000(최대 1000건)까지만 조회 가능하며,
  이는 API 자체의 상한이라 코드로 늘릴 수 없다.
- 기간(예: '3개월')으로 자르지 않고, 업종별로 가능한 최대치(최대 1000건)를
  그냥 전부 가져온다. 인기 업종은 1000건이 최근 며칠~몇 주치일 수 있고,
  비인기 업종은 1000건을 다 채우지 못하고 훨씬 적게 끝날 수 있다.

실행: python backfill_news.py
"""

import time

import requests

from news_common import (
    get_connection, now_kst, KST,
    INDUTY_NM, get_news_keyword, is_relevant,
    strip_tags, make_news_id, parse_pub_date,
    NAVER_CLIENT_ID, NAVER_CLIENT_SECRET,
)

MAX_START = 1000  # 네이버 뉴스 API의 start 파라미터 상한
PAGE_SIZE = 100    # 네이버 뉴스 API의 display 파라미터 상한


def fetch_with_retry(url, headers, params, max_retries=3, timeout=15):
    for attempt in range(1, max_retries + 1):
        try:
            resp = requests.get(url, headers=headers, params=params, timeout=timeout)
            resp.raise_for_status()
            return resp.json()
        except requests.exceptions.RequestException as e:
            print(f"    요청 실패 ({attempt}/{max_retries}회): {e}")
            if attempt == max_retries:
                raise
            time.sleep(3)


def fetch_industry_news_backfill(induty_cd):
    """
    최신순으로 API 최대치(1000건)까지 전부 가져온다.
    기간으로 자르지 않고, 나올 수 있는 만큼 다 가져와서 필터링만 거친다.
    """
    keyword = get_news_keyword(induty_cd)
    induty_nm = INDUTY_NM[induty_cd]

    url = "https://openapi.naver.com/v1/search/news.json"
    headers = {
        "X-Naver-Client-Id": NAVER_CLIENT_ID,
        "X-Naver-Client-Secret": NAVER_CLIENT_SECRET,
    }

    rows = []
    start = 1

    while start <= MAX_START:
        params = {"query": keyword, "display": PAGE_SIZE, "start": start, "sort": "date"}
        data = fetch_with_retry(url, headers, params)
        items = data.get("items", [])
        if not items:
            break  # 이 검색어로는 더 이상 결과가 없음 (1000건 전에 끝날 수 있음)

        for item in items:
            try:
                pub_date = parse_pub_date(item["pubDate"]).astimezone(KST)
            except (ValueError, KeyError):
                continue

            title = strip_tags(item["title"])
            description = strip_tags(item.get("description", ""))

            if not is_relevant(title, description, keyword):
                continue

            original_link = item.get("originallink") or item.get("link")
            rows.append({
                "news_id": make_news_id(original_link),
                "induty_cd": induty_cd,
                "induty_nm": induty_nm,
                "title": title,
                "original_link": original_link,
                "pub_date": pub_date,
            })

        start += PAGE_SIZE
        time.sleep(0.1)  # 과도한 연속 호출 방지

    return rows


def backfill_all_industries():
    all_rows = []

    for induty_cd, induty_nm in INDUTY_NM.items():
        rows = fetch_industry_news_backfill(induty_cd)

        if rows:
            oldest = min(r["pub_date"] for r in rows).strftime("%Y-%m-%d")
            newest = max(r["pub_date"] for r in rows).strftime("%Y-%m-%d")
            print(f"  {induty_nm}({induty_cd}) {len(rows)}건 수집 / 기간: {oldest} ~ {newest}")
        else:
            print(f"  {induty_nm}({induty_cd}) 0건")

        all_rows.extend(rows)

    return all_rows


# ============================================================
# DB 저장 (daily와 동일한 MERGE 방식, 중복은 자동 스킵)
# ============================================================
MERGE_NEWS_SQL = """
MERGE INTO INDUSTRY_NEWS tgt
USING (SELECT :news_id AS NEWS_ID FROM dual) src
ON (tgt.NEWS_ID = src.NEWS_ID)
WHEN NOT MATCHED THEN INSERT (
    NEWS_ID, INDUTY_CD, INDUTY_NM, TITLE, ORIGINAL_LINK, PUB_DATE, CACHED_AT, IS_VISIBLE
) VALUES (
    :news_id, :induty_cd, :induty_nm, :title, :original_link, :pub_date, :cached_at, 'Y'
)
"""


def save_news(rows):
    if not rows:
        print("INDUSTRY_NEWS: 저장할 데이터 없음")
        return
    conn = get_connection()
    now = now_kst()
    for row in rows:
        row["cached_at"] = now
    try:
        cursor = conn.cursor()
        cursor.executemany(MERGE_NEWS_SQL, rows)
        conn.commit()
        print(f"INDUSTRY_NEWS: {len(rows)}건 저장 시도 완료 (신규만 반영됨)")
    finally:
        cursor.close()
        conn.close()


# ============================================================
# 실행부
# ============================================================
if __name__ == "__main__":
    print("=== 업종별 뉴스 최대치(최대 1000건/업종) 수집 시작 ===")
    rows = backfill_all_industries()
    print(f"\n전체 필터 통과 {len(rows)}건 수집됨")

    print("=== DB 저장 ===")
    save_news(rows)

    print("=== 완료 ===")