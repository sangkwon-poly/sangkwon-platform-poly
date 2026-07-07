"""
업종 뉴스 - 매일 실행용 스크립트
- 네이버 뉴스 API 호출 (업종별, sort=date 최신순)
- 화이트리스트 필터링 통과한 것만 INDUSTRY_NEWS에 저장 (중복은 MERGE로 방지)
- 30일 지난 뉴스는 비노출(IS_VISIBLE='N') 처리

실행: python load_news_daily.py
(윈도우 작업 스케줄러 / 서버 크론에 매일 새벽 시간대로 등록해서 사용)
"""

import time
import requests

from news_common import (
    get_connection, now_kst,
    INDUTY_NM, get_news_keyword, is_relevant,
    strip_tags, make_news_id, parse_pub_date,
    NAVER_CLIENT_ID, NAVER_CLIENT_SECRET,
)


def fetch_with_retry(url, headers, params, max_retries=3, timeout=15):
    for attempt in range(1, max_retries + 1):
        try:
            resp = requests.get(url, headers=headers, params=params, timeout=timeout)
            resp.raise_for_status()
            return resp.json()
        except requests.exceptions.RequestException as e:
            print(f"  요청 실패 ({attempt}/{max_retries}회): {e}")
            if attempt == max_retries:
                raise
            time.sleep(3)


# ============================================================
# 1) 네이버 뉴스 API 호출 (업종별)
# ============================================================
def fetch_industry_news(induty_cd, display=100):
    keyword = get_news_keyword(induty_cd)
    induty_nm = INDUTY_NM[induty_cd]

    url = "https://openapi.naver.com/v1/search/news.json"
    headers = {
        "X-Naver-Client-Id": NAVER_CLIENT_ID,
        "X-Naver-Client-Secret": NAVER_CLIENT_SECRET,
    }
    params = {"query": keyword, "display": display, "sort": "date"}

    data = fetch_with_retry(url, headers, params)
    items = data.get("items", [])

    rows = []
    for item in items:
        title = strip_tags(item["title"])
        description = strip_tags(item.get("description", ""))

        if not is_relevant(title, description, keyword):
            continue

        try:
            pub_date = parse_pub_date(item["pubDate"])
        except (ValueError, KeyError):
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

    return rows


def fetch_all_industries_news():
    all_rows = []
    for induty_cd in INDUTY_NM:
        rows = fetch_industry_news(induty_cd)
        if rows:
            print(f"  {INDUTY_NM[induty_cd]}({induty_cd}) {len(rows)}건 수집")
        all_rows.extend(rows)
    return all_rows


# ============================================================
# 2) DB 저장 - INDUSTRY_NEWS (원문, 중복 방지 MERGE)
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


def hide_old_news(days=30):
    """30일 지난 뉴스는 삭제 대신 노출만 끔"""
    conn = get_connection()
    try:
        cursor = conn.cursor()
        cursor.execute(
            """
            UPDATE INDUSTRY_NEWS
            SET IS_VISIBLE = 'N'
            WHERE PUB_DATE < SYSDATE - :days
              AND IS_VISIBLE = 'Y'
            """,
            {"days": days},
        )
        conn.commit()
        print(f"INDUSTRY_NEWS: {days}일 지난 뉴스 {cursor.rowcount}건 비노출 처리")
    finally:
        cursor.close()
        conn.close()


# ============================================================
# 실행부
# ============================================================
if __name__ == "__main__":
    print("=== 업종별 뉴스 수집 시작 ===")
    news_rows = fetch_all_industries_news()
    print(f"전체 필터 통과 {len(news_rows)}건 수집됨")

    print("=== DB 저장 ===")
    save_news(news_rows)

    print("=== 30일 지난 뉴스 비노출 처리 ===")
    hide_old_news(days=30)

    print("=== 완료 ===")