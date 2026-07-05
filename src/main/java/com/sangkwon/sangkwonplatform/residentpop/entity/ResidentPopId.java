package com.sangkwon.sangkwonplatform.residentpop.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 상주인구 복합키 (분기 + 상권)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ResidentPopId implements Serializable {

    private String stdrYyquCd;
    private String trdarCd;
}
