package com.platform.curriculum.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.common.entity.CatalogStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.util.Assert;

@Entity
@Table(name = "subjects")
public class Subject extends AuditableEntity {

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9_]+$")
    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    protected Subject() {
        // JPA
    }

    private Subject(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.status = CatalogStatus.ACTIVE;
    }

    public static Subject create(String code, String name, String description) {
        Assert.hasText(code, "code must not be blank");
        Assert.isTrue(code.matches("^[A-Z0-9_]+$"), "code must be uppercase letters, digits, or underscores");
        Assert.hasText(name, "name must not be blank");
        return new Subject(code, name, description);
    }

    public void rename(String newName, String newDescription) {
        Assert.hasText(newName, "newName must not be blank");
        this.name = newName;
        this.description = newDescription;
    }

    public void archive() {
        requireStatus(CatalogStatus.ACTIVE, "archived");
        this.status = CatalogStatus.ARCHIVED;
    }

    public void restore() {
        requireStatus(CatalogStatus.ARCHIVED, "restored");
        this.status = CatalogStatus.ACTIVE;
    }

    private void requireStatus(CatalogStatus required, String attemptedTransition) {
        if (status != required) {
            throw new IllegalStateException("Subject %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CatalogStatus getStatus() {
        return status;
    }

}