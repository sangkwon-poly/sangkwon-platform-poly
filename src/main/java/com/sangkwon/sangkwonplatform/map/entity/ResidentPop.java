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

// 상주인구 (RESIDENT_POP) - 분기 x 상권
@Entity
@Table(name = "RESIDENT_POP")
@IdClass(ResidentPopId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResidentPop extends BaseEntity {

    @Id
    @Column(name = "STDR_YYQU_CD")
    private String stdrYyquCd;

    @Id
    @Column(name = "TRDAR_CD")
    private String trdarCd;

    @Column(name = "TOT_REPOP_CO")
    private Long totRepopCo;

    @Column(name = "ML_REPOP_CO")
    private Long mlRepopCo;

    @Column(name = "FML_REPOP_CO")
    private Long fmlRepopCo;

    @Column(name = "TOT_HSHLD_CO")
    private Long totHshldCo;
}
