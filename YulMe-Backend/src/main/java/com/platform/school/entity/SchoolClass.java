package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.common.entity.CatalogStatus;
import jakarta.persistence.*;
import org.springframework.util.Assert;
import java.util.UUID;

@Entity @Table(name = "school_classes")
public class SchoolClass extends AuditableEntity {
    @Column(name = "school_id", nullable = false, updatable = false) private UUID schoolId;
    @Column(name = "name", nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private CatalogStatus status;
    protected SchoolClass() {}
    private SchoolClass(UUID schoolId, String name) { this.schoolId = schoolId; this.name = name; this.status = CatalogStatus.ACTIVE; }
    public static SchoolClass create(UUID schoolId, String name) { Assert.notNull(schoolId, "schoolId must not be null"); Assert.hasText(name, "name must not be blank"); return new SchoolClass(schoolId, name); }
    public UUID getSchoolId() { return schoolId; } public String getName() { return name; } public CatalogStatus getStatus() { return status; }
}
