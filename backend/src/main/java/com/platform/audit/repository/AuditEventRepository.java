package com.platform.audit.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platform.audit.entity.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    
}
