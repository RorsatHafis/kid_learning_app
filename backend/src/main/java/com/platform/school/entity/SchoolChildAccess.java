package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/**
 * A family's grant to a school to see a child's school-assigned learning (V43, PD-01). Only the
 * child's family grants or revokes it. A revoked grant stays as history; a later grant is a new row.
 */
@Entity
@Table(name = "school_child_access")
public class SchoolChildAccess extends AuditableEntity {

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SchoolChildAccessStatus status;

    @Column(name = "granted_by_account_id", nullable = false, updatable = false)
    private UUID grantedByAccountId;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "revoked_by_account_id")
    private UUID revokedByAccountId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected SchoolChildAccess() {
        // JPA
    }

    public static SchoolChildAccess grant(UUID schoolId, UUID childId, UUID grantedBy, Instant now) {
        Assert.notNull(schoolId, "schoolId must not be null");
        Assert.notNull(childId, "childId must not be null");
        Assert.notNull(grantedBy, "grantedBy must not be null");
        SchoolChildAccess access = new SchoolChildAccess();
        access.schoolId = schoolId;
        access.childId = childId;
        access.status = SchoolChildAccessStatus.ACTIVE;
        access.grantedByAccountId = grantedBy;
        access.grantedAt = now;
        return access;
    }

    public void revoke(UUID revokedBy, Instant now) {
        if (status != SchoolChildAccessStatus.ACTIVE) {
            throw new IllegalStateException("This school's access has already ended");
        }
        status = SchoolChildAccessStatus.REVOKED;
        revokedByAccountId = revokedBy;
        revokedAt = now;
    }

    public UUID getSchoolId() { return schoolId; }
    public UUID getChildId() { return childId; }
    public SchoolChildAccessStatus getStatus() { return status; }
    public UUID getGrantedByAccountId() { return grantedByAccountId; }
    public Instant getGrantedAt() { return grantedAt; }
    public UUID getRevokedByAccountId() { return revokedByAccountId; }
    public Instant getRevokedAt() { return revokedAt; }
}
