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

// 집객시설 (ATTRACTION) - 분기 x 상권
@Entity
@Table(name = "ATTRACTION")
@IdClass(AttractionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attraction extends BaseEntity {

    @Id
    @Column(name = "STDR_YYQU_CD")
    private String stdrYyquCd;

    @Id
    @Column(name = "TRDAR_CD")
    private String trdarCd;

    @Column(name = "VIATR_FCLTY_CO")
    private Long viatrFcltyCo;

    @Column(name = "SUBWAY_STATN_CO")
    private Long subwayStatnCo;

    @Column(name = "BUS_STOP_CO")
    private Long busStopCo;

    @Column(name = "SCHOOL_CO")
    private Long schoolCo;

    @Column(name = "HOSPITAL_CO")
    private Long hospitalCo;

    @Column(name = "BANK_CO")
    private Long bankCo;
}
