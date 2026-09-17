package com.platform.common.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class ImmutableEvent extends BaseEntity {

    @Column(name = "occurred_at", insertable = false, updatable = false)
    private Instant occurredAt;
 
    public Instant getOccurredAt() {

        return occurredAt;
        
    }
    
}
