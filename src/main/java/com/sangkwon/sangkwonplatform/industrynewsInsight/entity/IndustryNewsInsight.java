package com.sangkwon.sangkwonplatform.industrynewsInsight.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

//복합키 테이블
@Entity
@Table(name = "INDUSTRY_NEWS_INSIGHT")
@IdClass(IndustryNewsInsight.PK.class)
@Getter
@Setter
@NoArgsConstructor
public class IndustryNewsInsight {

    @Id
    @Column(name = "INDUTY_CD", length = 20)
    private String indutyCd; //업종코드

    @Id
    @Column(name = "YEAR_MONTH", length = 7)
    private String yearMonth; //인사이트 발행 월

    @Column(name = "INDUTY_NM", length = 50, nullable = false)
    private String indutyNm; //업종명

    @Lob //긴 문자열
    @Column(name = "INSIGHT_TEXT", nullable = false)
    private String insightText; //LLM 인사이트

    @Column(name = "BASED_ON_COUNT")
    private Integer basedOnCount; //요약 생성에 근거로 쓴 필터 콩과 기사 건수

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    //복합키 클래스
    @Getter
    @Setter
    public static class PK implements Serializable{

        //ID
        private String indutyCd;
        private String yearMonth;

        public PK(String indutyCd, String yearMonth){
            this.indutyCd = indutyCd;
            this.yearMonth = yearMonth;
        }

        //indutyCd랑 yearMonth값이 같은 경우 같은 키
        @Override
        public boolean equals(Object o){
            if(this == o) return true;
            if(!(o instanceof  PK pk)) return false;
            return Objects.equals(indutyCd, pk.indutyCd) && Objects.equals(yearMonth, pk.yearMonth);
        }

        public int hashCode(){
            return Objects.hash(indutyCd, yearMonth);
        }
    }
}
