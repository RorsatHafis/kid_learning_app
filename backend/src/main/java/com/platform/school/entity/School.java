package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.common.entity.CatalogStatus;
import jakarta.persistence.*;
import org.springframework.util.Assert;
import java.util.UUID;

@Entity @Table(name = "schools")
public class School extends AuditableEntity {
    @Column(name = "name", nullable = false) private String name;
    @Column(name = "principal_account_id") private UUID principalAccountId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private CatalogStatus status;
    protected School() {}
    private School(String name, UUID principalAccountId) { this.name = name; this.principalAccountId = principalAccountId; this.status = CatalogStatus.ACTIVE; }
    public static School create(String name, UUID principalAccountId) { Assert.hasText(name, "name must not be blank"); Assert.notNull(principalAccountId, "principalAccountId must not be null"); return new School(name, principalAccountId); }
    public String getName() { return name; } public UUID getPrincipalAccountId() { return principalAccountId; } public CatalogStatus getStatus() { return status; }
}
