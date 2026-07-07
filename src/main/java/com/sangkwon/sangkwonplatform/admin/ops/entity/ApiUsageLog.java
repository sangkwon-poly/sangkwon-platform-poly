package com.sangkwon.sangkwonplatform.admin.ops.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "API_USAGE_LOG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiUsageLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USAGE_ID")
    private Long id;

    @Column(name = "API_NAME", nullable = false, length = 20)
    private String apiName;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "CALL_CNT", nullable = false)
    private long callCnt;

    @Column(name = "DAILY_LIMIT", nullable = false)
    private long dailyLimit;
}
