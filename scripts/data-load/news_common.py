"""
업종 뉴스 적재 - 공통 모듈
- DB 연결, 업종코드 매핑, 필터링, 유틸 함수 등
- load_news_daily.py, load_news_monthly.py 둘 다 이 모듈을 import해서 사용
"""

import os
import hashlib
from datetime import datetime, timezone, timedelta

import oracledb
from dotenv import load_dotenv

load_dotenv("properties.env")

DB_USERNAME = os.environ["DB_USERNAME"]
DB_PASSWORD = os.environ["DB_PASSWORD"]
DB_TNS_ALIAS = os.environ["DB_TNS_ALIAS"]
DB_WALLET_DIR = os.environ["DB_WALLET_DIR"]

NAVER_CLIENT_ID = os.environ["NAVER_CLIENT_ID"]
NAVER_CLIENT_SECRET = os.environ["NAVER_CLIENT_SECRET"]

# Thick 모드 활성화 (Instant Client 경로는 본인 환경에 맞게 수정)
oracledb.init_oracle_client(
    lib_dir=r"C:\Users\USER\Downloads\instantclient-basic-windows.x64-23.26.2.0.0\instantclient_23_0"
)
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
# 업종코드 -> 업종명(뉴스 검색용 키워드) 매핑
# ※ INDUTY 마스터의 표준명(예: '치킨전문점')을 그대로 검색어로 쓰면
#   실제 언론 표현('치킨집')과 달라 결과가 거의 안 나오는 게 검증됨.
#   NEWS_KEYWORD_OVERRIDE에 확인된 것만 실제 언론 표현으로 교체.
#   나머지는 우선 표준명 그대로 두되, 추후 검증하며 계속 보완 필요.
# ============================================================
INDUTY_NM = {
    "CS100001": "한식음식점", "CS100002": "중식음식점", "CS100003": "일식음식점",
    "CS100004": "양식음식점", "CS100005": "제과점", "CS100006": "패스트푸드점",
    "CS100007": "치킨전문점", "CS100008": "분식전문점", "CS100009": "호프-간이주점",
    "CS100010": "커피-음료",
    "CS200001": "일반교습학원", "CS200002": "외국어학원", "CS200003": "예술학원",
    "CS200004": "컴퓨터학원", "CS200005": "스포츠 강습", "CS200006": "일반의원",
    "CS200007": "치과의원", "CS200008": "한의원", "CS200009": "동물병원",
    "CS200010": "변호사사무소", "CS200011": "변리사사무소", "CS200012": "법무사사무소",
    "CS200013": "기타법무서비스", "CS200014": "회계사사무소", "CS200015": "세무사사무소",
    "CS200016": "당구장", "CS200017": "골프연습장", "CS200018": "볼링장",
    "CS200019": "PC방", "CS200020": "전자게임장", "CS200021": "기타오락장",
    "CS200022": "복권방", "CS200023": "통신기기수리", "CS200024": "스포츠클럽",
    "CS200025": "자동차수리", "CS200026": "자동차미용", "CS200027": "모터사이클수리",
    "CS200028": "미용실", "CS200029": "네일숍", "CS200030": "피부관리실",
    "CS200031": "세탁소", "CS200032": "가전제품수리", "CS200033": "부동산중개업",
    "CS200034": "여관", "CS200035": "게스트하우스", "CS200036": "고시원",
    "CS200037": "노래방", "CS200038": "독서실", "CS200039": "DVD방",
    "CS200040": "녹음실", "CS200041": "사진관", "CS200042": "통번역서비스",
    "CS200043": "건축물청소", "CS200044": "여행사", "CS200045": "비디오/서적임대",
    "CS200046": "의류임대", "CS200047": "가정용품임대",
    "CS300001": "슈퍼마켓", "CS300002": "편의점", "CS300003": "컴퓨터및주변장치판매",
    "CS300004": "핸드폰", "CS300005": "주류도매", "CS300006": "미곡판매",
    "CS300007": "육류판매", "CS300008": "수산물판매", "CS300009": "청과상",
    "CS300010": "반찬가게", "CS300011": "일반의류", "CS300012": "한복점",
    "CS300013": "유아의류", "CS300014": "신발", "CS300015": "가방",
    "CS300016": "안경", "CS300017": "시계및귀금속", "CS300018": "의약품",
    "CS300019": "의료기기", "CS300020": "서적", "CS300021": "문구",
    "CS300022": "화장품", "CS300023": "미용재료", "CS300024": "운동/경기용품",
    "CS300025": "자전거 및 기타운송장비", "CS300026": "완구", "CS300027": "섬유제품",
    "CS300028": "화초", "CS300029": "애완동물", "CS300030": "중고가구",
    "CS300031": "가구", "CS300032": "가전제품", "CS300033": "철물점",
    "CS300034": "악기", "CS300035": "인테리어", "CS300036": "조명용품",
    "CS300037": "중고차판매", "CS300038": "자동차부품", "CS300039": "모터사이클및부품",
    "CS300040": "재생용품 판매점", "CS300041": "예술품", "CS300042": "주유소",
    "CS300043": "전자상거래업",
}

# 검증 과정에서 실제 언론 표현이 표준명과 다르다고 확인된 것만 교체
NEWS_KEYWORD_OVERRIDE = {
    "CS100007": "치킨집",     # '치킨전문점' -> 실제 뉴스는 '치킨집'을 씀
    "CS200011": "변리사",     # '변리사사무소' -> 실제 뉴스는 '변리사'를 씀
}


def get_news_keyword(induty_cd):
    return NEWS_KEYWORD_OVERRIDE.get(induty_cd, INDUTY_NM[induty_cd])


# ============================================================
# 필터링 (화이트리스트, 제목 필수 포함)
# ============================================================
INCLUDE_KEYWORDS = [
    "매출", "폐업", "창업", "가맹점", "프랜차이즈",
    "원가", "물가", "인상", "인하", "매출액", "수익", "적자", "흑자",
]
EXCLUDE_KEYWORDS = ["화재", "불이 나", "구조", "진화", "소방"]


def is_relevant(title, description, keyword):
    if keyword not in title:
        return False
    text = title + description
    if any(kw in text for kw in EXCLUDE_KEYWORDS):
        return False
    return any(kw in text for kw in INCLUDE_KEYWORDS)


# ============================================================
# 공통 유틸
# ============================================================
def strip_tags(text):
    if not text:
        return ""
    return (text.replace("<b>", "").replace("</b>", "")
            .replace("&amp;", "&").replace("&quot;", '"'))


def make_news_id(original_link):
    return hashlib.md5(original_link.encode()).hexdigest()


def parse_pub_date(pub_date_str):
    # 예: 'Tue, 07 Jul 2026 10:01:00 +0900'
    return datetime.strptime(pub_date_str, "%a, %d %b %Y %H:%M:%S %z")