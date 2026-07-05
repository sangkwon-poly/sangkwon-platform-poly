package com.sangkwon.sangkwonplatform.store.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// 점포 통계 (STORE_STAT) - 분기 x 상권 x 업종
@Entity
@Table(name = "STORE_STAT")
@IdClass(StoreStatId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreStat extends BaseEntity {

    @Id
    @Column(name = "STDR_YYQU_CD")
    private String stdrYyquCd;

    @Id
    @Column(name = "TRDAR_CD")
    private String trdarCd;

    @Id
    @Column(name = "INDUTY_CD")
    private String indutyCd;

    @Column(name = "STOR_CO")
    private Long storCo;

    @Column(name = "SIMILR_INDUTY_STOR_CO")
    private Long similrIndutyStorCo;

    @Column(name = "OPBIZ_RT")
    private BigDecimal opbizRt;

    @Column(name = "CLSBIZ_RT")
    private BigDecimal clsbizRt;

    @Column(name = "OPBIZ_STOR_CO")
    private Long opbizStorCo;

    @Column(name = "CLSBIZ_STOR_CO")
    private Long clsbizStorCo;

    @Column(name = "FRC_STOR_CO")
    private Long frcStorCo;
}
