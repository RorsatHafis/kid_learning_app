package com.platform.curriculum.entity;

import com.platform.common.entity.PublishableVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

@Entity
@Table(name = "curriculum_versions")
public class CurriculumVersion extends PublishableVersion {

    @Column(name = "curriculum_id", nullable = false, updatable = false)
    private UUID curriculumId;

    protected CurriculumVersion() {
        // JPA
    }

    private CurriculumVersion(UUID curriculumId, int versionNumber) {
        super(versionNumber);
        this.curriculumId = curriculumId;
    }

    public static CurriculumVersion draft(UUID curriculumId, int versionNumber) {
        Assert.notNull(curriculumId, "curriculumId must not be null");
        return new CurriculumVersion(curriculumId, versionNumber);
    }

    public UUID getCurriculumId() {
        return curriculumId;
    }

}