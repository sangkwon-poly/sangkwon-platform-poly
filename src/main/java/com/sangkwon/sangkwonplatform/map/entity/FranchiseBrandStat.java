package com.sangkwon.sangkwonplatform.map.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 업종별 주요 프랜차이즈 (FRANCHISE_BRAND_STAT, 공정위 브랜드별 가맹점 현황)
@Entity
@Table(name = "FRANCHISE_BRAND_STAT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FranchiseBrandStat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STAT_ID")
    private Long statId;

    @Column(name = "INDUTY_CD")
    private String indutyCd;

    @Column(name = "BASE_YEAR")
    private Integer baseYear;

    @Column(name = "BRAND_NM")
    private String brandNm;

    @Column(name = "CORP_NM")
    private String corpNm;

    @Column(name = "FTC_INDUTY_NM")
    private String ftcIndutyNm;

    @Column(name = "FRCS_CNT")
    private Long frcsCnt;

    @Column(name = "AVG_SALES_AMT")
    private Long avgSalesAmt;

    @Column(name = "NEW_FRCS_RGS_CNT")
    private Long newFrcsRgsCnt;

    @Column(name = "CTRT_END_CNT")
    private Long ctrtEndCnt;

    @Column(name = "CTRT_CNCLTN_CNT")
    private Long ctrtCnclltnCnt;

    @Column(name = "NM_CHG_CNT")
    private Long nmChgCnt;
}
