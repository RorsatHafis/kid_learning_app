package com.platform.common.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * Base for entities that change: optimistic-locking version plus created and updated times kept by Hibernate.
 */
@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity {

    @Version
    @Column(name = "version", nullable = false)
    private long version;
 
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
 
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
 
    public long getVersion() {

        return version;

    }
 
    public Instant getCreatedAt() {

        return createdAt;

    }
 
    public Instant getUpdatedAt() {

        return updatedAt;
        
    }
    
}
