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

import java.time.LocalDate;
import java.time.LocalDateTime;

// 창업지원 사업 (기업마당 + K-Startup 통합, SOURCE_CD로 구분)
@Entity
@Table(name = "SUPPORT_PROGRAM")
@IdClass(SupportProgramId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportProgram extends BaseEntity {

    @Id
    @Column(name = "SOURCE_CD")
    private String sourceCd;

    @Id
    @Column(name = "PROGRAM_ID")
    private String programId;

    @Column(name = "TITLE")
    private String title;

    @Column(name = "PROGRAM_TYPE")
    private String programType;

    @Column(name = "TARGET")
    private String target;

    @Column(name = "REGION")
    private String region;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "APPLY_BGNG_DE")
    private LocalDate applyBgngDe;

    @Column(name = "APPLY_END_DE")
    private LocalDate applyEndDe;

    // 상시접수 등 날짜가 아닌 신청기간 원문 (기업마당 공고에만 채워짐)
    @Column(name = "APPLY_PERIOD_RAW")
    private String applyPeriodRaw;

    // 모집 진행 여부 (K-Startup 원본 제공값). 기업마당은 NULL
    @Column(name = "RECRUIT_YN")
    private String recruitYn;

    @Column(name = "IS_VISIBLE")
    private String isVisible;

    @Column(name = "CONTACT")
    private String contact;

    @Column(name = "DETAIL_URL")
    private String detailUrl;

    @Column(name = "SOURCE_REG_DT")
    private LocalDateTime sourceRegDt;

    // 관리자 노출/숨김 전환. 배치는 이 값을 건드리지 않으므로 노출 통제는 이 메서드로만 한다
    public void updateVisible(boolean visible) {
        this.isVisible = visible ? "Y" : "N";
    }
}
