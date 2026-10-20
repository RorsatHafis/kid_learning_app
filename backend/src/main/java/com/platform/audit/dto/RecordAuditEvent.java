package com.platform.audit.dto;

import java.util.Map;
import java.util.UUID;

import com.platform.audit.entity.AuditActorType;

/**
 * What a caller asks AuditService to record: who did what to which resource, with safe metadata only (no secrets
 * or children's personal details).
 */
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
