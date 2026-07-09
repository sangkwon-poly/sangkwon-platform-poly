package com.sangkwon.sangkwonplatform.support.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 지원사업 복합키 (출처 + 원본 사업 ID)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SupportProgramId implements Serializable {

    private String sourceCd;
    private String programId;
}
