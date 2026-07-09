package com.sangkwon.sangkwonplatform;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class SangkwonPlatformApplication {

    // '오늘' 경계 등 날짜 계산이 배포 환경 타임존에 흔들리지 않도록 KST로 고정한다
    @PostConstruct
    public void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(SangkwonPlatformApplication.class, args);
    }

}
