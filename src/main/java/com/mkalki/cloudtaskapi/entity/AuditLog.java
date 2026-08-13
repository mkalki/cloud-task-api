package com.mkalki.cloudtaskapi.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.mkalki.cloudtaskapi.enums.AuditAction;
import com.mkalki.cloudtaskapi.enums.AuditResourceType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    private AuditResourceType resourceType;
    private Long resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode changes;

    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;

    public AuditLog(
            Long userId,
            AuditAction action,
            AuditResourceType resourceType,
            Long resourceId,
            JsonNode changes,
            String ipAddress,
            String userAgent
    ) {
        this.userId = userId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.changes = changes;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = LocalDateTime.now();
    }

    protected AuditLog() {}
}
