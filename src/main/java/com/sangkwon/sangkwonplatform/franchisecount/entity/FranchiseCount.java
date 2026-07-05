package com.sangkwon.sangkwonplatform.franchisecount.entity;

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

import java.math.BigDecimal;

// 지역별 업종별 가맹점수 (FRANCHISE_COUNT, 공정위)
@Entity
@Table(name = "FRANCHISE_COUNT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FranchiseCount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FRC_CNT_ID")
    private Long frcCntId;

    @Column(name = "BASE_YEAR")
    private Integer baseYear;

    @Column(name = "AREA_CD")
    private String areaCd;

    @Column(name = "AREA_NM")
    private String areaNm;

    @Column(name = "INDUTY_NM")
    private String indutyNm;

    @Column(name = "FRC_CO")
    private Long frcCo;

    @Column(name = "FRC_RT")
    private BigDecimal frcRt;
}
