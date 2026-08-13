package com.mkalki.cloudtaskapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mkalki.cloudtaskapi.context.RequestContext;
import com.mkalki.cloudtaskapi.context.RequestContextProvider;
import com.mkalki.cloudtaskapi.entity.AuditLog;
import com.mkalki.cloudtaskapi.enums.AuditAction;
import com.mkalki.cloudtaskapi.enums.AuditResourceType;
import com.mkalki.cloudtaskapi.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final RequestContextProvider requestContextProvider;

    public AuditService(
            AuditLogRepository auditLogRepository,
            RequestContextProvider requestContextProvider
    ) {
        this.auditLogRepository = auditLogRepository;
        this.requestContextProvider = requestContextProvider;
    }

    public void log(
            AuditAction action,
            AuditResourceType resourceType,
            Long resourceId,
            JsonNode changes
    ){
        RequestContext context = requestContextProvider.getCurrentContext();

        AuditLog auditLog = new AuditLog(
                context.userId(),
                action,
                resourceType,
                resourceId,
                changes,
                context.ipAddress(),
                context.userAgent()
        );

        auditLogRepository.save(auditLog);
    }

    public void log(
            Long userId,
            AuditAction action,
            AuditResourceType resourceType,
            Long resourceId,
            JsonNode changes
    ) {
        RequestContext context = requestContextProvider.getContextForUser(userId);

        AuditLog auditLog = new AuditLog(
                context.userId(),
                action,
                resourceType,
                resourceId,
                changes,
                context.ipAddress(),
                context.userAgent()
        );

        auditLogRepository.save(auditLog);
    }
}
