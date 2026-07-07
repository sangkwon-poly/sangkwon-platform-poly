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

    // 요일별 매출
    @Column(name = "MON_SELNG_AMT")
    private Long monSelngAmt;

    @Column(name = "TUES_SELNG_AMT")
    private Long tuesSelngAmt;

    @Column(name = "WED_SELNG_AMT")
    private Long wedSelngAmt;

    @Column(name = "THUR_SELNG_AMT")
    private Long thurSelngAmt;

    @Column(name = "FRI_SELNG_AMT")
    private Long friSelngAmt;

    @Column(name = "SAT_SELNG_AMT")
    private Long satSelngAmt;

    @Column(name = "SUN_SELNG_AMT")
    private Long sunSelngAmt;

    // 시간대별 매출
    @Column(name = "TMZON_00_06_SELNG_AMT")
    private Long tmzon0006SelngAmt;

    @Column(name = "TMZON_06_11_SELNG_AMT")
    private Long tmzon0611SelngAmt;

    @Column(name = "TMZON_11_14_SELNG_AMT")
    private Long tmzon1114SelngAmt;

    @Column(name = "TMZON_14_17_SELNG_AMT")
    private Long tmzon1417SelngAmt;

    @Column(name = "TMZON_17_21_SELNG_AMT")
    private Long tmzon1721SelngAmt;

    @Column(name = "TMZON_21_24_SELNG_AMT")
    private Long tmzon2124SelngAmt;

    // 성별 매출
    @Column(name = "ML_SELNG_AMT")
    private Long mlSelngAmt;

    @Column(name = "FML_SELNG_AMT")
    private Long fmlSelngAmt;

    // 연령대별 매출
    @Column(name = "AGRDE_10_SELNG_AMT")
    private Long agrde10SelngAmt;

    @Column(name = "AGRDE_20_SELNG_AMT")
    private Long agrde20SelngAmt;

    @Column(name = "AGRDE_30_SELNG_AMT")
    private Long agrde30SelngAmt;

    @Column(name = "AGRDE_40_SELNG_AMT")
    private Long agrde40SelngAmt;

    @Column(name = "AGRDE_50_SELNG_AMT")
    private Long agrde50SelngAmt;

    @Column(name = "AGRDE_60_ABOVE_SELNG_AMT")
    private Long agrde60AboveSelngAmt;
}
