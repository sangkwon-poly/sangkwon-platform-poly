package com.sangkwon.sangkwonplatform.streetpop.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 길단위 유동인구 (STREET_POP) - 분기 x 상권
@Entity
@Table(name = "STREET_POP")
@IdClass(StreetPopId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreetPop extends BaseEntity {

    @Id
    @Column(name = "STDR_YYQU_CD")
    private String stdrYyquCd;

    @Id
    @Column(name = "TRDAR_CD")
    private String trdarCd;

    @Column(name = "TOT_FLPOP_CO")
    private Long totFlpopCo;

    @Column(name = "ML_FLPOP_CO")
    private Long mlFlpopCo;

    @Column(name = "FML_FLPOP_CO")
    private Long fmlFlpopCo;
}
