package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/**
 * A code a family enters to find a class (V43). It identifies the class and grants nothing: the
 * family decides, after seeing the school and what it will see (PLATFORM-EXPANSION.md §4).
 */
@Entity
@Table(name = "class_join_codes")
public class ClassJoinCode extends AuditableEntity {

    @Column(name = "school_class_id", nullable = false, updatable = false)
    private UUID schoolClassId;

    @Column(name = "code", nullable = false, updatable = false, length = 8)
    private String code;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ClassJoinCode() {
        // JPA
    }

    public static ClassJoinCode issue(UUID classId, String code, UUID createdBy, Instant expiresAt) {
        Assert.notNull(classId, "schoolClassId must not be null");
        Assert.hasText(code, "code must not be blank");
        Assert.notNull(createdBy, "createdBy must not be null");
        ClassJoinCode joinCode = new ClassJoinCode();
        joinCode.schoolClassId = classId;
        joinCode.code = code;
        joinCode.createdByAccountId = createdBy;
        joinCode.expiresAt = expiresAt;
        return joinCode;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    /** Usable: not revoked and not past its expiry. */
    public boolean isOpenAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public UUID getSchoolClassId() { return schoolClassId; }
    public String getCode() { return code; }
    public UUID getCreatedByAccountId() { return createdByAccountId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
