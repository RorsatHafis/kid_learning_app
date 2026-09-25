package com.platform.audit.entity;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.platform.common.entity.ImmutableEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "audit_events")
public class AuditEvent extends ImmutableEvent {
 
    @Column(name = "idempotency_record_id", updatable = false)
    private UUID idempotencyRecordId;
 
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 50)
    private AuditActorType actorType;
 
    @NotBlank
    @Column(name = "action", nullable = false, updatable = false, length = 100)
    private String action;
 
    @NotBlank
    @Column(name = "resource_type", nullable = false, updatable = false, length = 100)
    private String resourceType;
 
    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;
 
    @NotBlank
    @Column(name = "correlation_id", nullable = false, updatable = false, length = 128)
    private String correlationId;
 
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, updatable = false)
    private Map<String, Object> metadata;
 
    protected AuditEvent() {
        // JPA
    }
 
    private AuditEvent(UUID idempotencyRecordId, UUID actorId, AuditActorType actorType, String action,
                        String resourceType, UUID resourceId, String correlationId, Map<String, Object> metadata) {

        this.idempotencyRecordId = idempotencyRecordId;
        this.actorId = actorId;
        this.actorType = actorType;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.correlationId = correlationId;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);

    }
 
    public static AuditEvent occur(UUID idempotencyRecordId, UUID actorId, AuditActorType actorType, String action,
                                    String resourceType, UUID resourceId, String correlationId,
                                    Map<String, Object> metadata) {
                                        
        return new AuditEvent(idempotencyRecordId, actorId, actorType, action, resourceType, resourceId,
                correlationId, metadata);

    }
 
    public UUID getIdempotencyRecordId() {

        return idempotencyRecordId;

    }
 
    public UUID getActorId() {

        return actorId;

    }
 
    public AuditActorType getActorType() {

        return actorType;

    }
 
    public String getAction() {

        return action;

    }
 
    public String getResourceType() {

        return resourceType;

    }
 
    public UUID getResourceId() {

        return resourceId;

    }
 
    public String getCorrelationId() {

        return correlationId;

    }
 
    public Map<String, Object> getMetadata() {

        return metadata;

    }
 
}
