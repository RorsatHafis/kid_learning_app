package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.*;
import org.springframework.util.Assert;
import java.time.LocalDate;
import java.util.UUID;

/** M12 P1: a term (semester, trimester...) inside an academic year; terms of a year never overlap. */
@Entity @Table(name = "academic_terms")
public class AcademicTerm extends AuditableEntity {

    @Column(name = "academic_year_id", nullable = false, updatable = false) private UUID academicYearId;
    @Column(name = "name", nullable = false) private String name;
    @Column(name = "starts_on", nullable = false) private LocalDate startsOn;
    @Column(name = "ends_on", nullable = false) private LocalDate endsOn;

    protected AcademicTerm() {}

    private AcademicTerm(UUID academicYearId, String name, LocalDate startsOn, LocalDate endsOn) {
        this.academicYearId = academicYearId;
        this.name = name;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    public static AcademicTerm create(UUID academicYearId, String name, LocalDate startsOn, LocalDate endsOn) {
        Assert.notNull(academicYearId, "academicYearId must not be null");
        Assert.hasText(name, "name must not be blank");
        Assert.isTrue(endsOn.isAfter(startsOn), "a term ends after it starts");
        return new AcademicTerm(academicYearId, name, startsOn, endsOn);
    }

    public UUID getAcademicYearId() { return academicYearId; }
    public String getName() { return name; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }

    public void change(String name, LocalDate startsOn, LocalDate endsOn) {
        Assert.hasText(name, "name must not be blank");
        Assert.isTrue(endsOn.isAfter(startsOn), "a term ends after it starts");
        this.name = name;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }
}
