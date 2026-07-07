package com.sangkwon.sangkwonplatform.map.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// AI 리포트 생성 이력 (LLM_REPORT)
@Entity
@Table(name = "LLM_REPORT")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@lombok.AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LlmReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REPORT_ID")
    private Long reportId;

    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "TRDAR_CD")
    private String trdarCd;

    @Column(name = "STDR_YYQU_CD")
    private String stdrYyquCd;

    @Lob
    @Column(name = "PROMPT")
    private String prompt;

    @Lob
    @Column(name = "RESULT_TEXT")
    private String resultText;

    @Column(name = "MODEL_NAME")
    private String modelName;

    @Column(name = "TOKEN_CNT")
    private Long tokenCnt;
}
