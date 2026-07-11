package com.sangkwon.sangkwonplatform.industrytrademark.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// 실제 KIPRIS API를 전 업종에 대해 호출해 공유 DB에 적재하는 수동 실행기라 일반 빌드에서는 돌리지 않는다.
// 데이터 갱신이 필요할 때 애노테이션을 잠시 풀고 실행한다(어드민 데이터 적재 트리거로도 가능).
@Disabled("KIPRIS 라이브 API 적재. 수동 실행 전용")
@SpringBootTest
@ActiveProfiles("local")
class IndustryTrademarkLoadManualTest {

    @Autowired
    private IndustryTrademarkBatchService service;

    @Autowired
    private JdbcTemplate jt;

    @Test
    void 업종_상표_동향_적재() {
        long loaded = service.load();
        System.out.println("적재 건수: " + loaded);
        jt.queryForList("SELECT COUNT(DISTINCT INDUTY_CD) INDUTIES, COUNT(*) ROWS_CNT, "
                        + "MIN(APPL_DATE) OLDEST, MAX(APPL_DATE) NEWEST FROM INDUSTRY_TRADEMARK")
                .forEach(System.out::println);
        System.out.println("--- 치킨(CS100007) ---");
        jt.queryForList("SELECT TITLE, APPLICANT_NM, APPL_DATE, STATUS "
                        + "FROM INDUSTRY_TRADEMARK WHERE INDUTY_CD = 'CS100007' ORDER BY APPL_DATE DESC")
                .forEach(System.out::println);
        System.out.println("--- 커피-음료(CS100010) ---");
        jt.queryForList("SELECT TITLE, APPLICANT_NM, APPL_DATE, STATUS "
                        + "FROM INDUSTRY_TRADEMARK WHERE INDUTY_CD = 'CS100010' ORDER BY APPL_DATE DESC")
                .forEach(System.out::println);
    }
}
