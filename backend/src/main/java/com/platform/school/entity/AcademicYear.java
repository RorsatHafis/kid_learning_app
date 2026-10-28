package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.common.entity.CatalogStatus;
import jakarta.persistence.*;
import org.springframework.util.Assert;
import java.time.LocalDate;
import java.util.UUID;

/**
 * M12 P1: one of a school's academic years - its own dates, set by the school (calendars differ between schools).
 * At most one is current (V77 partial unique index). Archiving keeps it and its classes as history.
 */
@Entity @Table(name = "academic_years")
public class AcademicYear extends AuditableEntity {

    @Column(name = "school_id", nullable = false, updatable = false) private UUID schoolId;
    @Column(name = "name", nullable = false) private String name;
    @Column(name = "starts_on", nullable = false) private LocalDate startsOn;
    @Column(name = "ends_on", nullable = false) private LocalDate endsOn;
    @Column(name = "is_current", nullable = false) private boolean current;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private CatalogStatus status;

    protected AcademicYear() {}

    private AcademicYear(UUID schoolId, String name, LocalDate startsOn, LocalDate endsOn) {
        this.schoolId = schoolId;
        this.name = name;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.status = CatalogStatus.ACTIVE;
    }

    public static AcademicYear create(UUID schoolId, String name, LocalDate startsOn, LocalDate endsOn) {
        Assert.notNull(schoolId, "schoolId must not be null");
        Assert.hasText(name, "name must not be blank");
        Assert.isTrue(endsOn.isAfter(startsOn), "a year ends after it starts");
        return new AcademicYear(schoolId, name, startsOn, endsOn);
    }

    public UUID getSchoolId() { return schoolId; }
    public String getName() { return name; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public boolean isCurrent() { return current; }
    public boolean isArchived() { return status == CatalogStatus.ARCHIVED; }

    public void change(String name, LocalDate startsOn, LocalDate endsOn) {
        Assert.hasText(name, "name must not be blank");
        Assert.isTrue(endsOn.isAfter(startsOn), "a year ends after it starts");
        this.name = name;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    public void makeCurrent() { Assert.state(!isArchived(), "an archived year can't be current"); current = true; }
    public void clearCurrent() { current = false; }

    public void archive() {
        Assert.state(!current, "the current year can't be archived");
        Assert.state(status == CatalogStatus.ACTIVE, "year is already archived");
        status = CatalogStatus.ARCHIVED;
    }

    public void restore() { Assert.state(status == CatalogStatus.ARCHIVED, "year is not archived"); status = CatalogStatus.ACTIVE; }

    /** Whether [startsOn, endsOn] and [otherStart, otherEnd] share a day. */
    public static boolean overlaps(LocalDate startsOn, LocalDate endsOn, LocalDate otherStart, LocalDate otherEnd) {
        return !startsOn.isAfter(otherEnd) && !otherStart.isAfter(endsOn);
    }
}
