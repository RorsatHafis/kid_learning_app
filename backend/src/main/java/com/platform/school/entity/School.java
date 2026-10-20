package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.common.entity.CatalogStatus;
import jakarta.persistence.*;
import org.springframework.util.Assert;
import java.util.UUID;

/**
 * A school, run by its principal. Archiving keeps its classes and history.
 */
@Entity @Table(name = "schools")
public class School extends AuditableEntity {
    @Column(name = "name", nullable = false) private String name;
    @Column(name = "principal_account_id") private UUID principalAccountId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private CatalogStatus status;
    protected School() {}
    private School(String name, UUID principalAccountId) { this.name = name; this.principalAccountId = principalAccountId; this.status = CatalogStatus.ACTIVE; }
    public static School create(String name, UUID principalAccountId) { Assert.hasText(name, "name must not be blank"); Assert.notNull(principalAccountId, "principalAccountId must not be null"); return new School(name, principalAccountId); }
    /** PD-26 (Y17): the school stops being used - nothing new; everything existing stays. */
    public void archive() { if (status == CatalogStatus.ARCHIVED) throw new IllegalStateException("This school is already archived."); status = CatalogStatus.ARCHIVED; }
    public void restore() { if (status != CatalogStatus.ARCHIVED) throw new IllegalStateException("This school is not archived."); status = CatalogStatus.ACTIVE; }
    public boolean isArchived() { return status == CatalogStatus.ARCHIVED; }
    public String getName() { return name; } public UUID getPrincipalAccountId() { return principalAccountId; } public CatalogStatus getStatus() { return status; }
    public void rename(String name) { Assert.hasText(name, "name must not be blank"); this.name = name.trim(); }
}
