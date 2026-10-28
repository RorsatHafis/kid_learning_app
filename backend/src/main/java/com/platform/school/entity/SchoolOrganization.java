package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.common.entity.CatalogStatus;
import jakarta.persistence.*;
import org.springframework.util.Assert;
import java.util.UUID;

/**
 * M12 B0: a school organization - one school, or a group of schools - with one or more branches (`schools`). Its
 * owner oversees every branch; each branch keeps its own principal, classes, calendar, staff, plan and payments.
 * Student records (P2) will belong here, so a transfer between branches keeps one record (decision S1).
 */
@Entity @Table(name = "school_organizations")
public class SchoolOrganization extends AuditableEntity {

    @Column(name = "name", nullable = false) private String name;
    @Column(name = "owner_account_id") private UUID ownerAccountId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private CatalogStatus status;

    protected SchoolOrganization() {}

    private SchoolOrganization(String name, UUID ownerAccountId) {
        this.name = name;
        this.ownerAccountId = ownerAccountId;
        this.status = CatalogStatus.ACTIVE;
    }

    public static SchoolOrganization create(String name, UUID ownerAccountId) {
        Assert.hasText(name, "name must not be blank");
        Assert.notNull(ownerAccountId, "ownerAccountId must not be null");
        return new SchoolOrganization(name, ownerAccountId);
    }

    public String getName() { return name; }
    public UUID getOwnerAccountId() { return ownerAccountId; }
    public boolean isArchived() { return status == CatalogStatus.ARCHIVED; }
    public void rename(String name) { Assert.hasText(name, "name must not be blank"); this.name = name.trim(); }
}
