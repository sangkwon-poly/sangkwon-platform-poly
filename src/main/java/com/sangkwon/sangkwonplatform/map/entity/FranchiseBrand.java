package com.sangkwon.sangkwonplatform.map.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 프랜차이즈 브랜드 목록 (FRANCHISE_BRAND, 공정위)
@Entity
@Table(name = "FRANCHISE_BRAND")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FranchiseBrand extends BaseEntity {

    @Id
    @Column(name = "BRAND_MGMT_NO")
    private String brandMgmtNo;

    @Column(name = "BRAND_NM")
    private String brandNm;

    @Column(name = "CORP_NM")
    private String corpNm;

    @Column(name = "INDUTY_LCLAS_NM")
    private String indutyLclasNm;

    @Column(name = "INDUTY_MLSFC_NM")
    private String indutyMlsfcNm;

    @Column(name = "BIZ_START_DE")
    private LocalDate bizStartDe;
}
