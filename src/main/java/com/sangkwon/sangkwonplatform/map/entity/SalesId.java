package com.sangkwon.sangkwonplatform.map.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 추정매출 복합키 (분기 + 상권 + 업종)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SalesId implements Serializable {

    private String stdrYyquCd;
    private String trdarCd;
    private String indutyCd;
}
