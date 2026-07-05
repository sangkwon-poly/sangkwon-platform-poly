package com.sangkwon.sangkwonplatform.map.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// 상권 마스터 (TRDAR)
@Entity
@Table(name = "TRDAR")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trdar extends BaseEntity {

    @Id
    @Column(name = "TRDAR_CD")
    private String trdarCd;

    @Column(name = "TRDAR_CD_NM")
    private String trdarCdNm;

    @Column(name = "TRDAR_SE_CD")
    private String trdarSeCd;

    @Column(name = "TRDAR_SE_CD_NM")
    private String trdarSeCdNm;

    @Column(name = "SIGNGU_CD")
    private String signguCd;

    @Column(name = "SIGNGU_NM")
    private String signguNm;

    @Column(name = "ADSTRD_CD")
    private String adstrdCd;

    @Column(name = "ADSTRD_NM")
    private String adstrdNm;

    @Column(name = "CENTER_LOT")
    private BigDecimal centerLot;

    @Column(name = "CENTER_LAT")
    private BigDecimal centerLat;
}
