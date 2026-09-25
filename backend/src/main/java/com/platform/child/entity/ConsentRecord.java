package com.platform.child.entity;

import java.util.UUID;

import com.platform.common.entity.ImmutableEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "consent_records")
public class ConsentRecord extends ImmutableEvent {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;
 
    @Column(name = "granted_by_account_id", nullable = false, updatable = false)
    private UUID grantedByAccountId;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 50)
    private ConsentType consentType;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ConsentAction action;
 
    protected ConsentRecord() {
        // JPA
    }
 
    private ConsentRecord(UUID childId, UUID grantedByAccountId, ConsentType consentType, ConsentAction action) {

        this.childId = childId;
        this.grantedByAccountId = grantedByAccountId;
        this.consentType = consentType;
        this.action = action;

    }
 
    public static ConsentRecord record(UUID childId, UUID grantedByAccountId,
                                        ConsentType consentType, ConsentAction action) {

        return new ConsentRecord(childId, grantedByAccountId, consentType, action);
        
    }
 
    public UUID getChildId() {
        return childId;
    }
 
    public UUID getGrantedByAccountId() {
        return grantedByAccountId;
    }
 
    public ConsentType getConsentType() {
        return consentType;
    }
 
    public ConsentAction getAction() {
        return action;
    }
    
}
