package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.common.entity.CatalogStatus;
import jakarta.persistence.*;
import org.springframework.util.Assert;
import java.util.UUID;

/**
 * A class within a school; archiving keeps its members and history.
 */
@Entity @Table(name = "school_classes")
public class SchoolClass extends AuditableEntity {
    @Column(name = "school_id", nullable = false, updatable = false) private UUID schoolId;
    @Column(name = "name", nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private CatalogStatus status;
    /** M12 P1: the academic year the class belongs to; null for classes made before years existed (V77). */
    @Column(name = "academic_year_id") private UUID academicYearId;
    protected SchoolClass() {}
    private SchoolClass(UUID schoolId, String name) { this.schoolId = schoolId; this.name = name; this.status = CatalogStatus.ACTIVE; }
    public static SchoolClass create(UUID schoolId, String name) { Assert.notNull(schoolId, "schoolId must not be null"); Assert.hasText(name, "name must not be blank"); return new SchoolClass(schoolId, name); }
    public UUID getSchoolId() { return schoolId; } public String getName() { return name; } public CatalogStatus getStatus() { return status; }
    public boolean isArchived() { return status == CatalogStatus.ARCHIVED; }
    public UUID getAcademicYearId() { return academicYearId; }
    public void placeInYear(UUID academicYearId) { this.academicYearId = academicYearId; }
    public void rename(String name) { Assert.hasText(name, "name must not be blank"); this.name = name.trim(); }
    /** X17 (PD-25): no new uses - no joining, assigning or placing; nothing its children have changes. */
    public void archive() { Assert.state(status == CatalogStatus.ACTIVE, "class is already archived"); status = CatalogStatus.ARCHIVED; }
    public void restore() { Assert.state(status == CatalogStatus.ARCHIVED, "class is not archived"); status = CatalogStatus.ACTIVE; }
}
