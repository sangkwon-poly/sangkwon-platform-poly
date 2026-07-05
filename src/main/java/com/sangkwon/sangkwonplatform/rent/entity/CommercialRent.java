package com.sangkwon.sangkwonplatform.rent.entity;

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

// 상업용 부동산 임대 지표 (COMMERCIAL_RENT, 한국부동산원)
@Entity
@Table(name = "COMMERCIAL_RENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialRent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RENT_ID")
    private Long rentId;

    @Column(name = "REGION_CD")
    private String regionCd;

    @Column(name = "REGION_NM")
    private String regionNm;

    @Column(name = "RLST_TY_CD")
    private String rlstTyCd;

    @Column(name = "METRIC_CD")
    private String metricCd;

    @Column(name = "METRIC_NM")
    private String metricNm;

    @Column(name = "STDR_YYQU_CD")
    private String stdrYyquCd;

    @Column(name = "METRIC_VALUE")
    private BigDecimal metricValue;

    @Column(name = "UOM")
    private String uom;
}
