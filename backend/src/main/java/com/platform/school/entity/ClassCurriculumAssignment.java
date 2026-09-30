package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * A curriculum a class assigned (V43). It is what the school may see of a member's learning, and
 * what a child joining the class later is enrolled in.
 */
@Entity
@Table(name = "class_curriculum_assignments")
public class ClassCurriculumAssignment extends AuditableEntity {

    @Column(name = "school_class_id", nullable = false, updatable = false)
    private UUID schoolClassId;

    @Column(name = "curriculum_id", nullable = false, updatable = false)
    private UUID curriculumId;

    @Column(name = "assigned_by_account_id", nullable = false)
    private UUID assignedByAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClassCurriculumAssignmentStatus status;

    protected ClassCurriculumAssignment() {
        // JPA
    }

    public static ClassCurriculumAssignment assign(UUID classId, UUID curriculumId, UUID assignedBy) {
        Assert.notNull(classId, "schoolClassId must not be null");
        Assert.notNull(curriculumId, "curriculumId must not be null");
        Assert.notNull(assignedBy, "assignedBy must not be null");
        ClassCurriculumAssignment assignment = new ClassCurriculumAssignment();
        assignment.schoolClassId = classId;
        assignment.curriculumId = curriculumId;
        assignment.assignedByAccountId = assignedBy;
        assignment.status = ClassCurriculumAssignmentStatus.ACTIVE;
        return assignment;
    }

    /** Assigned again after being removed. */
    public void reassign(UUID assignedBy) {
        assignedByAccountId = assignedBy;
        status = ClassCurriculumAssignmentStatus.ACTIVE;
    }

    public void remove() {
        status = ClassCurriculumAssignmentStatus.REMOVED;
    }

    public UUID getSchoolClassId() { return schoolClassId; }
    public UUID getCurriculumId() { return curriculumId; }
    public UUID getAssignedByAccountId() { return assignedByAccountId; }
    public ClassCurriculumAssignmentStatus getStatus() { return status; }
}
