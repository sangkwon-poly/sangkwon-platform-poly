package com.sangkwon.sangkwonplatform.support.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 지원 사업 상세 (K-Startup 전용, SUPPORT_PROGRAM과 1:0또는1)
@Entity
@Table(name = "SUPPORT_PROGRAM_KSTARTUP_DETAIL")
@IdClass(SupportProgramId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportProgramKstartupDetail extends BaseEntity {

    @Id
    @Column(name = "SOURCE_CD")
    private String sourceCd;

    @Id
    @Column(name = "PROGRAM_ID")
    private String programId;

    @Column(name = "APLY_MTHD_VST")
    private String aplyMthdVst;

    @Column(name = "APLY_MTHD_PSSR")
    private String aplyMthdPssr;

    @Column(name = "APLY_MTHD_FAX")
    private String aplyMthdFax;

    // 원본 자체가 암호화된 문자열로 제공되는 경우가 있어 그대로 표시하지 않는다
    @Column(name = "APLY_MTHD_EML")
    private String aplyMthdEml;

    @Column(name = "APLY_MTHD_ONLI")
    private String aplyMthdOnli;

    @Column(name = "APLY_MTHD_ETC")
    private String aplyMthdEtc;

    @Lob
    @Column(name = "APLY_EXCL_TRGT_CTNT")
    private String aplyExclTrgtCtnt;

    @Column(name = "BIZ_ENYY")
    private String bizEnyy;

    @Column(name = "BIZ_TRGT_AGE")
    private String bizTrgtAge;

    @Lob
    @Column(name = "PRFN_MATR")
    private String prfnMatr;

    @Column(name = "SPRV_INST")
    private String sprvInst;

    @Column(name = "PBANC_NTRP_NM")
    private String pbancNtrpNm;

    @Column(name = "BIZ_GDNC_URL")
    private String bizGdncUrl;

    @Column(name = "INTG_PBANC_YN")
    private String intgPbancYn;
}
