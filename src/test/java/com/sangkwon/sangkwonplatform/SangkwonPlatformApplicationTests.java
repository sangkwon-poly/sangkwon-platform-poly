package com.sangkwon.sangkwonplatform;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 전체 컨텍스트 로딩 검증. Oracle ADB(지갑)에 실제로 붙으므로 DB 없는 환경에선 -PexcludeIntegration 으로 제외된다.
@Tag("integration")
@SpringBootTest
class SangkwonPlatformApplicationTests {

    @Test
    void contextLoads() {
    }

}
