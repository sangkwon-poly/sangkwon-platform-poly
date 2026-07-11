package com.sangkwon.sangkwonplatform.map.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// 실제 공정위 API를 전량 호출해 공유 DB에 적재하는 수동 실행기라 일반 빌드에서는 돌리지 않는다.
// 연 단위 데이터 갱신이 필요할 때 애노테이션을 잠시 풀고 실행한다(어드민 데이터 적재 트리거로도 가능).
@Disabled("공정위 라이브 API 적재. 수동 실행 전용")
@SpringBootTest
@ActiveProfiles("local")
class FranchiseBrandStatLoadManualTest {

    @Autowired
    private FranchiseBrandStatLoadService service;

    @Autowired
    private JdbcTemplate jt;

    @Test
    void 주요_프랜차이즈_적재() {
        long loaded = service.load();
        System.out.println("적재 건수: " + loaded);
        jt.queryForList("SELECT INDUTY_CD, MIN(FTC_INDUTY_NM) FTC_NM, MIN(BASE_YEAR) YR, COUNT(*) CNT "
                        + "FROM FRANCHISE_BRAND_STAT GROUP BY INDUTY_CD ORDER BY INDUTY_CD")
                .forEach(System.out::println);
        System.out.println("--- 치킨(CS100007) 상위 ---");
        jt.queryForList("SELECT BRAND_NM, CORP_NM, FRCS_CNT, AVG_SALES_AMT "
                        + "FROM FRANCHISE_BRAND_STAT WHERE INDUTY_CD = 'CS100007' ORDER BY FRCS_CNT DESC")
                .forEach(System.out::println);
    }
}
