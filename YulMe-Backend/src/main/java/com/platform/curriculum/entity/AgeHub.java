package com.platform.curriculum.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.util.Assert;

@Entity
@Table(name = "age_hubs")
public class AgeHub extends AuditableEntity {

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9_]+$")
    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "min_age", nullable = false)
    private int minAge;

    /** Nullable: Mastery is explicitly open-ended ("14-18+"). */
    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected AgeHub() {
        // JPA
    }

    private AgeHub(String code, String name, int minAge, Integer maxAge, int displayOrder) {
        this.code = code;
        this.name = name;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.displayOrder = displayOrder;
    }

    public static AgeHub create(String code, String name, int minAge, Integer maxAge, int displayOrder) {
        Assert.hasText(code, "code must not be blank");
        Assert.isTrue(code.matches("^[A-Z0-9_]+$"), "code must be uppercase letters, digits, or underscores");
        Assert.hasText(name, "name must not be blank");
        Assert.isTrue(minAge >= 0, "minAge must not be negative");
        Assert.isTrue(maxAge == null || maxAge >= minAge, "maxAge must not be less than minAge");
        return new AgeHub(code, name, minAge, maxAge, displayOrder);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getMinAge() {
        return minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    /** True if {@code age} falls within this hub's range (max_age null means open-ended). */
    public boolean includesAge(int age) {
        return age >= minAge && (maxAge == null || age <= maxAge);
    }

}