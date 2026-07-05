package com.sangkwon.sangkwonplatform.store.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 점포 통계 복합키 (분기 + 상권 + 업종)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StoreStatId implements Serializable {

    private String stdrYyquCd;
    private String trdarCd;
    private String indutyCd;
}
