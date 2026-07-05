package com.sangkwon.sangkwonplatform.map.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 추정매출 (SALES) - 분기 x 상권 x 업종
@Entity
@Table(name = "SALES")
@IdClass(SalesId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sales extends BaseEntity {

    @Id
    @Column(name = "STDR_YYQU_CD")
    private String stdrYyquCd;

    @Id
    @Column(name = "TRDAR_CD")
    private String trdarCd;

    @Id
    @Column(name = "INDUTY_CD")
    private String indutyCd;

    @Column(name = "THSMON_SELNG_AMT")
    private Long thsmonSelngAmt;

    @Column(name = "THSMON_SELNG_CO")
    private Long thsmonSelngCo;
}
