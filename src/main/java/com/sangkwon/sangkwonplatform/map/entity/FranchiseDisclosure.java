package com.sangkwon.sangkwonplatform.map.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 정보공개서 목록 (FRANCHISE_DISCLOSURE, 공정위)
@Entity
@Table(name = "FRANCHISE_DISCLOSURE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FranchiseDisclosure extends BaseEntity {

    @Id
    @Column(name = "DISCLOSURE_SN")
    private String disclosureSn;

    @Column(name = "CORP_NM")
    private String corpNm;

    @Column(name = "BRAND_NM")
    private String brandNm;

    @Column(name = "BIZ_REG_NO")
    private String bizRegNo;

    @Column(name = "VIEWER_URL")
    private String viewerUrl;
}
