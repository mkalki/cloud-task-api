package com.mkalki.cloudtaskapi.repository;

import com.mkalki.cloudtaskapi.entity.AuditLog;
import org.springframework.data.repository.Repository;

public interface AuditLogRepository extends Repository<AuditLog,Long> {

    AuditLog save(AuditLog auditLog);
}
