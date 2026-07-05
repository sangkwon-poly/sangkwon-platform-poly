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

import java.math.BigDecimal;

// 아파트 정보 (APT) - 분기 x 상권
@Entity
@Table(name = "APT")
@IdClass(AptId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Apt extends BaseEntity {

    @Id
    @Column(name = "STDR_YYQU_CD")
    private String stdrYyquCd;

    @Id
    @Column(name = "TRDAR_CD")
    private String trdarCd;

    @Column(name = "APT_COMPLX_CO")
    private Long aptComplxCo;

    @Column(name = "APT_HSHLD_CO")
    private Long aptHshldCo;

    @Column(name = "AVRG_AREA")
    private BigDecimal avrgArea;

    @Column(name = "AVRG_MRKT_PRC")
    private Long avrgMrktPrc;
}
