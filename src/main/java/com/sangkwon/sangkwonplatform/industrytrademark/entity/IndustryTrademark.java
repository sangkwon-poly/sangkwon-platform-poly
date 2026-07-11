package com.sangkwon.sangkwonplatform.industrytrademark.entity;

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

import java.time.LocalDate;

// 업종별 상표 출원 동향 (INDUSTRY_TRADEMARK, KIPRIS)
@Entity
@Table(name = "INDUSTRY_TRADEMARK")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndustryTrademark extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TM_ID")
    private Long tmId;

    @Column(name = "INDUTY_CD")
    private String indutyCd;

    @Column(name = "APPL_NO")
    private String applNo;

    @Column(name = "TITLE")
    private String title;

    @Column(name = "APPLICANT_NM")
    private String applicantNm;

    @Column(name = "APPL_DATE")
    private LocalDate applDate;

    @Column(name = "STATUS")
    private String status;
}
