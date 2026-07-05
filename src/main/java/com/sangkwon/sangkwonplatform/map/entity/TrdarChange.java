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

// 상권 변화지표 (TRDAR_CHANGE) - 분기 x 상권
@Entity
@Table(name = "TRDAR_CHANGE")
@IdClass(TrdarChangeId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrdarChange extends BaseEntity {

    @Id
    @Column(name = "STDR_YYQU_CD")
    private String stdrYyquCd;

    @Id
    @Column(name = "TRDAR_CD")
    private String trdarCd;

    @Column(name = "TRDAR_CHNGE_IX")
    private String trdarChngeIx;

    @Column(name = "TRDAR_CHNGE_IX_NM")
    private String trdarChngeIxNm;

    @Column(name = "OPR_SALE_MT_AVRG")
    private BigDecimal oprSaleMtAvrg;

    @Column(name = "CLS_SALE_MT_AVRG")
    private BigDecimal clsSaleMtAvrg;
}
