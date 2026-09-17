package com.platform.knowledge.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * A logical grouping of skills (e.g. "Addition &amp; Subtraction"). Maps 1:1 to
 * {@code skill_groups} (V11 migration) - no status column on that table, unlike
 * Skill; a group is a lighter-weight organizational concept than a skill itself.
 */
@Entity
@Table(name = "skill_groups")
public class SkillGroup extends AuditableEntity {

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @NotBlank
    @Column(name = "code", nullable = false, updatable = false, length = 60)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    protected SkillGroup() {
        // JPA
    }

    private SkillGroup(UUID subjectId, String code, String name) {
        this.subjectId = subjectId;
        this.code = code;
        this.name = name;
    }

    public static SkillGroup create(UUID subjectId, String code, String name) {
        Assert.notNull(subjectId, "subjectId must not be null");
        Assert.hasText(code, "code must not be blank");
        Assert.hasText(name, "name must not be blank");
        return new SkillGroup(subjectId, code, name);
    }

    public void rename(String newName) {
        Assert.hasText(newName, "newName must not be blank");
        this.name = newName;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

}