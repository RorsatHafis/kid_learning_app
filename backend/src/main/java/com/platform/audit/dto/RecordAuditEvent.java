package com.platform.audit.dto;

import java.util.Map;
import java.util.UUID;

import com.platform.audit.entity.AuditActorType;

public record RecordAuditEvent(
    UUID idempotencyRecordId,
    UUID actorId,
    AuditActorType actorType,
    String action,
    String resourceType,
    UUID resourceId,
    Map<String, Object> metadata
) {
    
}
