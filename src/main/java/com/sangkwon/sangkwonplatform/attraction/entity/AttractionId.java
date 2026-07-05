package com.sangkwon.sangkwonplatform.attraction.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 집객시설 복합키 (분기 + 상권)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AttractionId implements Serializable {

    private String stdrYyquCd;
    private String trdarCd;
}
