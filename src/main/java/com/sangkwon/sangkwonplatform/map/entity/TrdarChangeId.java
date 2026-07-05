package com.sangkwon.sangkwonplatform.map.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 상권 변화지표 복합키 (분기 + 상권)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrdarChangeId implements Serializable {

    private String stdrYyquCd;
    private String trdarCd;
}
