package com.platform.audit.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platform.audit.entity.AuditEvent;

/**
 * Persistence for the audit log. Lines are only ever added; the database refuses changes.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    
}
