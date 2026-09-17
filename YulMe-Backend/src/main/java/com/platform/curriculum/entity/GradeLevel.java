package com.platform.curriculum.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.Assert;

import java.util.UUID;

@Entity
@Table(name = "grade_levels")
public class GradeLevel extends AuditableEntity {

    @Column(name = "age_hub_id", nullable = false, updatable = false)
    private UUID ageHubId;

    @NotBlank
    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected GradeLevel() {
        // JPA
    }

    private GradeLevel(UUID ageHubId, String code, String name, int displayOrder) {
        this.ageHubId = ageHubId;
        this.code = code;
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public static GradeLevel create(UUID ageHubId, String code, String name, int displayOrder) {
        Assert.notNull(ageHubId, "ageHubId must not be null");
        Assert.hasText(code, "code must not be blank");
        Assert.hasText(name, "name must not be blank");
        return new GradeLevel(ageHubId, code, name, displayOrder);
    }

    public UUID getAgeHubId() {
        return ageHubId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

}