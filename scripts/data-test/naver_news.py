"""
개선된 '업종' 검색 전략 재검증 스크립트

지난 검증에서 발견된 문제:
1. query에 "상권"을 넣으니 검색결과 자체가 "상권"이라는 단어 위주로 편향됨
2. 화이트리스트에도 "상권"이 있어서 검색어와 필터가 같은 단어를 중복 확인 (필터 무력화)
3. description에만 업종명이 스치듯 언급된, 실제로 무관한 기사가 다수 통과함
   (예: "다이소 이벤트", "롯데하이마트 혜택" 등이 편의점 검색에 섞여 들어옴)

개선안:
1. query에서 "상권" 제거 → 업종명만으로 검색
2. 화이트리스트에서 "상권", "경기"처럼 너무 광범위한 단어 제외
3. 제목(title)에 업종명 자체가 반드시 포함되어야 한다는 조건 추가 (핵심 개선)
"""

import os
import requests
from datetime import datetime
from dotenv import load_dotenv

load_dotenv("properties.env")

CLIENT_ID = os.environ["NAVER_CLIENT_ID"]
CLIENT_SECRET = os.environ["NAVER_CLIENT_SECRET"]

SAMPLE_INDUSTRIES = {
    "인기": "치킨집",       # INDUTY_NM 표준명 "치킨전문점" -> 실제 언론 표현으로 교체
    "보통": "편의점",       # 표준명과 언론 표현이 동일한 케이스
    "비인기": "변리사",     # "변리사사무소" -> 실제로는 "변리사"로 언급되는 경우가 많음
}

# '상권', '경기'처럼 너무 광범위한 단어 제외, 실질적 경제신호 단어만 남김
INCLUDE_KEYWORDS = [
    "매출", "폐업", "창업", "가맹점", "프랜차이즈",
    "원가", "물가", "인상", "인하", "매출액", "수익", "적자", "흑자",
]

# 화재/사고성 노이즈는 여전히 소수 있을 수 있어 최소한의 블랙리스트도 유지
EXCLUDE_KEYWORDS = ["화재", "불이 나", "구조", "진화", "소방"]


def matched_keywords(text):
    return [kw for kw in INCLUDE_KEYWORDS if kw in text]


def fetch_news(query, display=100):
    url = "https://openapi.naver.com/v1/search/news.json"
    headers = {
        "X-Naver-Client-Id": CLIENT_ID,
        "X-Naver-Client-Secret": CLIENT_SECRET,
    }
    params = {"query": query, "display": display, "sort": "date"}
    resp = requests.get(url, headers=headers, params=params, timeout=15)
    resp.raise_for_status()
    return resp.json().get("items", [])


def strip_tags(text):
    return (text.replace("<b>", "").replace("</b>", "")
            .replace("&amp;", "&").replace("&quot;", '"'))


def analyze(keyword, label, days_window=1, max_print=15):
    print(f"\n{'=' * 60}")
    print(f"[{label}] 검색어(개선판): '{keyword}' (상권 제거) / 최근 {days_window}일")
    print(f"{'=' * 60}")

    # 개선 1: query에서 "상권" 제거, 업종명만으로 검색
    items = fetch_news(keyword, display=100)
    now = datetime.now().astimezone()

    total_in_window = 0
    title_has_keyword = []      # 제목에 업종명은 포함된 것 (화이트리스트 통과 여부 무관)
    matched_items = []          # 최종 화이트리스트까지 통과한 것

    for item in items:
        try:
            pub = datetime.strptime(item["pubDate"], "%a, %d %b %Y %H:%M:%S %z")
        except (ValueError, KeyError):
            continue

        days_ago = (now - pub).days
        if days_ago > days_window:
            continue

        total_in_window += 1
        title = strip_tags(item["title"])
        desc = strip_tags(item["description"])
        full_text = title + desc

        # 개선 3: 제목에 업종명 자체가 반드시 포함되어야 함
        if keyword not in title:
            continue

        title_has_keyword.append(title)

        # 블랙리스트 (사고성 기사 제외)
        if any(kw in full_text for kw in EXCLUDE_KEYWORDS):
            continue

        kws = matched_keywords(full_text)
        if kws:
            matched_items.append({
                "title": title,
                "matched_keywords": kws,
                "pub_date": pub.strftime("%m-%d %H:%M"),
            })

    print(f"기간 내 전체 수집: {total_in_window}건")
    print(f"→ 제목에 '{keyword}' 포함된 기사: {len(title_has_keyword)}건")
    print(f"→ 그 중 화이트리스트까지 통과: {len(matched_items)}건\n")

    if title_has_keyword and not matched_items:
        print(f"[디버깅] 제목에 '{keyword}'는 있지만 화이트리스트에서 탈락한 기사들:")
        for i, t in enumerate(title_has_keyword[:max_print], 1):
            print(f"  {i:2}. {t}")
        print()

    for i, m in enumerate(matched_items[:max_print], 1):
        print(f"{i:2}. [{m['pub_date']}] {m['title']}")
        print(f"    └ 걸린 키워드: {m['matched_keywords']}")

    if total_in_window > 0:
        precision_estimate = len(matched_items) / total_in_window * 100
        print(f"\n(참고) 기간 내 전체 대비 필터 통과 비율: {precision_estimate:.1f}%")
        print("       ※ 이 비율은 '얼마나 걸러졌는지'이며, 실제 관련성(정밀도)은")
        print("         위 제목 목록을 직접 읽어보고 판단해야 함")


if __name__ == "__main__":
    for label, keyword in SAMPLE_INDUSTRIES.items():
        analyze(keyword, label, days_window=1)