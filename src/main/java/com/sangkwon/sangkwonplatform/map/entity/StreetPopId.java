package com.sangkwon.sangkwonplatform.map.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 길단위 유동인구 복합키 (분기 + 상권)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StreetPopId implements Serializable {

    private String stdrYyquCd;
    private String trdarCd;
}
