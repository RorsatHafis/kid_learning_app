package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.*;
import org.springframework.util.Assert;
import java.util.UUID;

/**
 * A child's membership of one class. Separate from the school's access to the child and from teacher assignment:
 * each is checked on its own.
 */
@Entity @Table(name = "class_enrollments")
public class ClassEnrollment extends AuditableEntity {
    @Column(name="school_class_id", nullable=false, updatable=false) private UUID schoolClassId;
    @Column(name="child_id", nullable=false, updatable=false) private UUID childId;
    @Enumerated(EnumType.STRING) @Column(name="status",nullable=false) private ClassEnrollmentStatus status;
    protected ClassEnrollment() {}
    private ClassEnrollment(UUID classId, UUID childId) { schoolClassId=classId; this.childId=childId; status=ClassEnrollmentStatus.ACTIVE; }
    public static ClassEnrollment enroll(UUID classId, UUID childId) { Assert.notNull(classId,"schoolClassId must not be null"); Assert.notNull(childId,"childId must not be null"); return new ClassEnrollment(classId,childId); }
    /** The child leaves the class: the family ended the school's access, or the class removed them. */
    public void remove() { status=ClassEnrollmentStatus.REMOVED; }
    /** The child joins the class again (only ever with an ACTIVE grant, SchoolJoinService). */
    public void reactivate() { status=ClassEnrollmentStatus.ACTIVE; }
    public UUID getSchoolClassId(){return schoolClassId;} public UUID getChildId(){return childId;} public ClassEnrollmentStatus getStatus(){return status;}
}
