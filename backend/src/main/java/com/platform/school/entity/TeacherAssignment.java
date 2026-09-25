package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.*;
import org.springframework.util.Assert;
import java.util.UUID;

@Entity @Table(name = "teacher_assignments")
public class TeacherAssignment extends AuditableEntity {
    @Column(name = "school_class_id", nullable = false, updatable = false) private UUID schoolClassId;
    @Column(name = "teacher_account_id", nullable = false, updatable = false) private UUID teacherAccountId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private TeacherAssignmentStatus status;
    protected TeacherAssignment() {}
    private TeacherAssignment(UUID classId, UUID teacherId) { schoolClassId=classId; teacherAccountId=teacherId; status=TeacherAssignmentStatus.ACTIVE; }
    public static TeacherAssignment assign(UUID classId, UUID teacherId) { Assert.notNull(classId,"schoolClassId must not be null"); Assert.notNull(teacherId,"teacherAccountId must not be null"); return new TeacherAssignment(classId,teacherId); }
    public UUID getSchoolClassId(){return schoolClassId;} public UUID getTeacherAccountId(){return teacherAccountId;} public TeacherAssignmentStatus getStatus(){return status;}
}
