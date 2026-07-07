package com.sangkwon.sangkwonplatform.admin.ops.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ADMIN_AUDIT_LOG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUDIT_ID")
    private Long id;

    @Column(name = "ADMIN_ID", nullable = false)
    private Long adminId;

    @Column(name = "ACTION", nullable = false, length = 50)
    private String action;

    @Column(name = "TARGET_TYPE", length = 50)
    private String targetType;

    @Column(name = "TARGET_ID", length = 100)
    private String targetId;

    @Lob
    @Column(name = "DETAIL")
    private String detail;

    @Column(name = "IP_ADDR", length = 45)
    private String ipAddr;

    private AdminAuditLog(Long adminId, String action, String targetType,
                         String targetId, String detail, String ipAddr) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.ipAddr = ipAddr;
    }

    public static AdminAuditLog of(Long adminId, String action, String targetType,
                                   String targetId, String detail, String ipAddr) {
        return new AdminAuditLog(adminId, action, targetType, targetId, detail, ipAddr);
    }
}
