package com.platform.family.entity;

import com.platform.common.entity.AuditableEntity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "memberships")
public class Membership extends AuditableEntity {

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;
 
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MembershipRole role;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status;
 
    protected Membership() {
        // JPA
    }
 
    private Membership(UUID familyId, UUID accountId, MembershipRole role) {
        
        this.familyId = familyId;
        this.accountId = accountId;
        this.role = role;
        this.status = MembershipStatus.ACTIVE;

    }
 
    public static Membership create(UUID familyId, UUID accountId, MembershipRole role) {

        return new Membership(familyId, accountId, role);

    }
 
    /** Soft-removes the membership. The row is kept (not deleted) so it can be reused if the account rejoins. */
    public void remove() {

        requireStatus(MembershipStatus.ACTIVE, "removed");
        this.status = MembershipStatus.REMOVED;

    }
 
    public void reactivate() {

        requireStatus(MembershipStatus.REMOVED, "reactivated");
        this.status = MembershipStatus.ACTIVE;

    }
 
    private void requireStatus(MembershipStatus required, String attemptedTransition) {

        if (status != required) {

            throw new IllegalStateException("Membership %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));

        }

    }
 
    public UUID getFamilyId() {
        return familyId;
    }
 
    public UUID getAccountId() {
        return accountId;
    }
 
    public MembershipRole getRole() {
        return role;
    }
 
    public MembershipStatus getStatus() {
        return status;
    }
 
    
}
