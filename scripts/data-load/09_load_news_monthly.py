"""
업종 뉴스 - 매달 1회 실행용 스크립트
- 6개월(180일) 지난 원문 완전 삭제
  (6개월 트렌드 그래프는 별도 집계 테이블 없이, 조회 시점에
   INDUSTRY_NEWS를 바로 GROUP BY 해서 보여주는 방식으로 결정함
   -> 데이터량이 적어 실시간 집계로도 부담 없고, 별도 테이블/배치를
      유지보수할 필요가 없어짐)

실행: python load_news_monthly.py
(윈도우 작업 스케줄러 / 서버 크론에 매달 1일로 등록해서 사용)
"""

from news_common import get_connection


def delete_very_old_news(days=180):
    """6개월(180일) 지난 원문은 완전 삭제"""
    conn = get_connection()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "DELETE FROM INDUSTRY_NEWS WHERE PUB_DATE < SYSDATE - :days",
            {"days": days},
        )
        conn.commit()
        print(f"INDUSTRY_NEWS: {days}일 지난 뉴스 {cursor.rowcount}건 완전 삭제")
    finally:
        cursor.close()
        conn.close()


# ============================================================
# 실행부
# ============================================================
if __name__ == "__main__":
    print("=== 6개월 지난 원문 정리 ===")
    delete_very_old_news(days=180)

    print("=== 완료 ===")